package ru.inversion.edo.xxl.xxi.command.mi_0600;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.error.XXLExceptionLogger;
import ru.inversion.edo.xxl.xxi.repo.InfRole;
import ru.inversion.utils.U;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;


@Configuration
@RequiredArgsConstructor
@Slf4j
public class MI_0600_Scheduler implements SchedulingConfigurer
{
   private final MI_0600_FileCollector fileCollector;
   private final MI_0600_ZipService    zipService;
   private final MI_0600_Repository    repository;

   /**
    * Период scan хранится в PG на уровне WSP 600.
    * <p>
    * Следующий запуск рассчитывается после завершения предыдущего,
    * поэтому экземпляр scheduler не запускает два scan одновременно.
    */
   @Override
   public void configureTasks(ScheduledTaskRegistrar taskRegistrar)
   {
      taskRegistrar.addTriggerTask (
              this::run,
              context ->
              {
                 Duration scanDelay = repository.getScanDelay();

                 if( scanDelay == null || scanDelay.isZero() || scanDelay.isNegative() )
                     throw new IllegalStateException( "MI_0600 scanDelay must be positive" );

                 Instant lastCompletion = context.lastCompletion();

                 if( lastCompletion == null )
                     return Instant.now();

                 return lastCompletion.plus(scanDelay);
              }
      );
   }


   /** */
   private void run()
   {
      sendPending();
      captureFiles();
   }


   /**
    * Ищет новые файлы по всем inf_id WSP 600.
    */
   private void captureFiles()
   {
      final List<InfConfig> configs;

      try {
         configs = repository.loadInfConfigs(InfRole.Initiator);
      }
      catch( Exception e ) {
         log.error( "MI_0600 failed to load MI_inf configuration", e );
         return;
      }

      for( InfConfig config : configs )
      {
         try {
            processInf(config);
         }
         catch( Exception e ) {
            /* Ошибка одного inf_id не должна останавливать обработку остальных видов сведений. */
            XXLExceptionLogger.log(
               log,
               e,
               "MI_0600 file scan failed",
               U.toMap( "inf_id", config.infId(), "role", InfRole.Initiator )
            );
         }
      }
   }


   /** */
   private void processInf( InfConfig config )
   {
      /*
       * Вид сведений временно отключен.
       */
      if( !config.enabled() )
      {
         fileCollector.reset(config.infId());
         return;
      }

      /* WorkDir отсутствует - это ошибка */
      if( config.workDir() == null )
      {
         throw Errors.config (
            "Для вида сведений " + config.infId() + " не настроена рабочая директория",
            U.toMap("inf_id", config.infId(), "role", InfRole.Initiator )
         );
      }

      fileCollector.collect(
         config.infId(),
         config.workDir(),
         config.collectDelay()
      )
      .ifPresent (
         batch -> captureBatch(config, batch)
      );
   }


   /**
    * Фиксирует snapshot файлов в PostgreSQL.
    * <p>
    * После успешного createRequest() PostgreSQL является source для дальнейшей отправки.
    */
   private void captureBatch( InfConfig config, MI_0600_FileCollector.FileBatch batch )
   {
      // Снимок состояния исходных файлов ДО обработки.
      Map<Path, FileStamp> snapshot = snapshotFiles(batch.files());

      List<String> fileNames = batch.files()
         .stream()
            .map(path -> path.getFileName().toString())
              .toList();

      // Проверяем, какие файлы уже зарегистрированы в PG.
      Set<String> registered = repository.findRegisteredFileNames( config.infId(), fileNames );

      // В новый ZIP включаем только ещё не зарегистрированные файлы.
      List<Path> newFiles = batch.files()
         .stream()
            .filter(path -> !registered.contains(path.getFileName().toString()))
              .toList();

      if( !registered.isEmpty() )
      {
         log.info( "MI_0600 already registered files: infId={}, count={}", config.infId(), registered.size());
      }

      if( newFiles.isEmpty() )
          return;

      MI_0600_FileCollector.FileBatch newBatch = new MI_0600_FileCollector.FileBatch( newFiles, batch.startedAt(), batch.infId() );

      List<String> newFileNames = newFiles
         .stream()
            .map(path -> path.getFileName().toString())
              .toList();

      Path zipPath = zipService.createZip(newBatch);

      final MI_0600_Repository.CreateResult created;

      try
      {
         created = repository.createRequest( config.infId(), zipPath, newFileNames );

         /* Файлы в карантин */
         quarantineRegisteredFiles (
            config,
            newFiles,
            Set.copyOf(newFileNames),
            snapshot
         );

         if( config.makeZipCopy() && config.zipCopyDir() != null )
         {
            try
            {
               Files.copy(
                       zipPath,
                       config.zipCopyDir().resolve(zipPath.getFileName()),
                       REPLACE_EXISTING
               );
            }
            catch( IOException e )
            {
               log.warn( "MI_0600 ZIP backup failed: infId={}, zip={}", config.infId(), zipPath.getFileName(), e );
            }
         }
      }
      finally {
         deleteTempZip(zipPath);
      }

      log.info( "MI_0600 batch captured: infId={}, reqId={}, itmId={}, newFiles={}, skippedFiles={}", config.infId(), created.reqId(), created.itmId(), newFiles.size(), registered.size() );
   }


   /**
    * Отправляет накопленные в PG request, которым сейчас разрешена отправка.
    * <p>
    * Условия отправки - производственный календарь, выходные, праздники,
    * временные окна и выбор req_id полностью находятся в PG.
    */
   private void sendPending()
   {
      try {
         repository.sendPending();
      }
      catch( Exception e )
      {
         /*
          * Capture уже выполненных batch от этого не откатывается.
          * ZIP остаются в PG и будут рассмотрены следующим scan.
          */
         log.warn( "MI_0600 send pending failed. Будут обработаны в след scan ", e );
      }
   }


   /** */
   private void deleteSourceFiles( List<Path> files, long infId, long reqId )
   {
      for( Path file : files ) {
         try {
            Files.deleteIfExists(file);
         }
         catch( IOException e ) {
            log.warn( "MI_0600 source file cleanup failed: infId={}, reqId={}, file={}", infId, reqId, file.getFileName(), e );
         }
      }
   }


   /** */
   private void deleteTempZip(Path zipPath)
   {
      if( zipPath == null )
          return;
      try {
         Files.deleteIfExists(zipPath);
      }
      catch( IOException e ) {
         log.warn( "MI_0600 temporary ZIP cleanup failed: file={}", zipPath.getFileName(), e );
      }
   }


   private record FileStamp(
           long size,
           FileTime modifiedAt,
           Object fileKey
   )
   {}

   /** Снимок метаданных до обращения к PG. */
   private Map<Path, FileStamp> snapshotFiles(List<Path> files)
   {
      Map<Path, FileStamp> result = new HashMap<>();

      for( Path file : files )
      {
         try {
            result.put(file, readFileStamp(file));
         }
         catch( IOException e ) {
            throw new UncheckedIOException(
                    "MI_0600 failed to inspect file: " + file, e
            );
         }
      }

      return Map.copyOf(result);
   }

   /** Не следуем по символическим ссылкам. */
   private FileStamp readFileStamp(Path file) throws IOException
   {
      BasicFileAttributes attrs = Files.readAttributes(
              file,
              BasicFileAttributes.class,
              LinkOption.NOFOLLOW_LINKS
      );

      if( !attrs.isRegularFile() )
         throw new IOException("Not a regular file: " + file);

      return new FileStamp(
              attrs.size(),
              attrs.lastModifiedTime(),
              attrs.fileKey()
      );
   }

   /**
    * Изолирует ранее зарегистрированные файлы.
    *
    * Ничего не удаляет и не перезаписывает.
    * При любой неопределённости исходный файл остаётся в SEND_DIR.
    */
   private void quarantineRegisteredFiles(
           InfConfig config,
           List<Path> files,
           Set<String> registered,
           Map<Path, FileStamp> snapshot
   )
   {
      if( registered.isEmpty() )
         return;

      Path quarantineRoot = config.workDir()
              .resolve(".xxl_0600_quarantine")
              .resolve(Integer.toString(config.infId()));

      for( Path source : files )
      {
         if( !registered.contains(source.getFileName().toString()) )
            continue;

         FileStamp expected = snapshot.get(source);

         // Без fileKey нельзя достаточно надёжно проверить,
         // что путь по-прежнему указывает на тот же файл.
         if( expected == null || expected.fileKey() == null )
         {
            log.warn("MI_0600 quarantine skipped: file identity unavailable, file={}", source);
            continue;
         }

         Path slot = null;

         try
         {
            if( !expected.equals(readFileStamp(source)) )
            {
               log.warn("MI_0600 quarantine skipped: file changed, file={}", source);
               continue;
            }

            Files.createDirectories(quarantineRoot);

            // Уникальный каталог гарантирует отсутствие конфликта имён.
            slot = Files.createTempDirectory(quarantineRoot, "registered-");

            Path target = slot.resolve(source.getFileName());

            // Повторная проверка непосредственно перед перемещением.
            if( !expected.equals(readFileStamp(source)) )
            {
               log.warn("MI_0600 quarantine skipped: file changed before move, file={}", source);
               continue;
            }

            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);

            // Если файл изменился во время перемещения, сохраняем его
            // для ручной проверки. Не удаляем и не возвращаем автоматически.
            if( !expected.equals(readFileStamp(target)) )
            {
               log.error(
                       "MI_0600 quarantined file differs from snapshot: infId={}, target={}",
                       config.infId(), target
               );
            }
            else
            {
               log.info(
                       "MI_0600 registered file quarantined: infId={}, source={}, target={}",
                       config.infId(), source, target
               );
            }
         }
         catch( AtomicMoveNotSupportedException e )
         {
            log.warn("MI_0600 atomic move unsupported, file remains in SEND_DIR: {}", source, e);
         }
         catch( IOException e )
         {
            log.warn("MI_0600 quarantine failed: file={}", source, e);
         }
         finally
         {
            // Убираем только пустой временный каталог.
            // Каталог с перемещённым файлом не трогаем.
            if( slot != null )
            {
               try {
                  Files.deleteIfExists(slot);
               }
               catch( DirectoryNotEmptyException ignored ) {
                  // Файл сохранён в карантине.
               }
               catch( IOException e ) {
                  log.warn("MI_0600 quarantine directory cleanup failed: {}", slot, e);
               }
            }
         }
      }
   }

}