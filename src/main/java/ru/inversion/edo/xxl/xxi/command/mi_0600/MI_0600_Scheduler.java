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
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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


   /** Метод запуска */
   private void run()
   {
      /* Сначала отправляем готовые zip */
      sendPending();

      /* Потом собираем новые */
      captureFiles();
   }


   /**
    * Ищет новые файлы по всем inf_id WSP 600.
    */
   private void captureFiles()
   {
      final List<InfConfig> configs;

      try {
         /* Конфиги ВС */
         configs = repository.loadInfConfigs( InfRole.Initiator );
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
      /* Вид сведений временно отключен. */
      if( !config.enabled() )
      {
         log.info("Вид сведений {} отключен", config.infId() );

         fileCollector.reset(config.infId());
         return;
      }

      /* WorkDir отсутствует - это ошибка */
      if( config.workDir() == null )
      {
         XXLExceptionLogger.log (
            log,
            Errors.config ( "Для вида сведений " + config.infId() + " не настроена рабочая директория", U.toMap("inf_id", config.infId(), "role", InfRole.Initiator ) ),
            "Пропуск обработки директории для " + config.infId()
         );

         /*
         throw Errors.config (
            "Для вида сведений " + config.infId() + " не настроена рабочая директория",
            U.toMap("inf_id", config.infId(), "role", InfRole.Initiator )
         );
         */
      }

      fileCollector
         .collect( config.infId(), config.workDir(), config.collectDelay() )
         .ifPresent (
            batch -> captureBatch(config, batch)
         );
   }


   /**
    * Фиксирует snapshot файлов в PostgreSQL.
    * <p>
    * После успешного createRequest() PostgreSQL является source для дальнейшей отправки.
    */
   private void captureBatch(
        InfConfig config,
        MI_0600_FileCollector.FileBatch batch
   )
   {
      List<String> fileNames = batch.files()
         .stream()
            .map(path -> path.getFileName().toString())
              .toList();

      Set<String> registered = repository.findRegisteredFileNames( config.infId(), fileNames );

      /*
       * Эти файлы уже гарантированно зарегистрированы в PG.
       * Имена бизнес-файлов по контракту уникальны.
       */
      cleanupRegisteredFiles( config, batch.files(), registered );

      List<Path> newFiles = batch.files()
         .stream()
            .filter(path ->!registered.contains(path.getFileName().toString()) )
              .toList();

      if( newFiles.isEmpty() )
          return;

      List<String> newFileNames = newFiles
              .stream()
              .map(path -> path.getFileName().toString())
              .toList();

      MI_0600_FileCollector.FileBatch newBatch =
              new MI_0600_FileCollector.FileBatch(
                      newFiles,
                      batch.startedAt(),
                      batch.infId()
              );

      Path processedDir = config.workDir().resolve(".processed");

      Path zipPath      = zipService.createZip(newBatch, processedDir);

      final MI_0600_Repository.CreateResult created;

      try
      {
         created = repository.createRequest( config.infId(), zipPath, newFileNames );
      }
      catch( RuntimeException e )
      {
         /*
          * PG не зарегистрировал batch.
          * Исходники остаются на месте.
          */
         deleteFile(zipPath);
         throw e;
      }

      /*
       * Необязательная дополнительная копия ZIP.
       * Сам ZIP уже есть и в PG, и в .processed.
       */
      if( config.makeZipCopy() && config.zipCopyDir() != null )
      {
         try
         {
            Files.createDirectories(config.zipCopyDir());

            Files.copy (
                 zipPath,
                 config.zipCopyDir().resolve(zipPath.getFileName()),
                 REPLACE_EXISTING
            );
         }
         catch( IOException e )
         {
            log.warn( "MI_0600 ZIP backup failed: infId={}, zip={}",
                    config.infId(), zipPath.getFileName(), e );
         }
      }

      /* Чистим */
      cleanupProcessedSourceFiles( config, newFiles, created.reqId() );

      /* Сообщаем */
      log.info(
         "MI_0600 batch captured: infId={}, reqId={}, itmId={}, zip={}, newFiles={}, skippedFiles={}",
         config.infId(), created.reqId(), created.itmId(), zipPath.getFileName(), newFiles.size(), registered.size()
      );
   }


   /** */
   private void deleteFile(Path file)
   {
      if( file == null )
         return;

      try {
         Files.deleteIfExists(file);
      }
      catch( IOException e ) {
         log.warn( "MI_0600 file cleanup failed: file={}", file, e );
      }
   }

   /** */
   private void cleanupRegisteredFiles(
      InfConfig config,
      List<Path> files,
      Set<String> registered
   )
   {
      for( Path file : files )
      {
         if( !registered.contains(file.getFileName().toString()) )
            continue;
         deleteSourceFile( config.infId(), null, file, "already registered" );
      }
   }

   /** */
   private void cleanupProcessedSourceFiles(
      InfConfig config,
      List<Path> files,
      long reqId
   )
   {
      for( Path file : files )
      {
         deleteSourceFile( config.infId(), reqId, file, "registered" );
      }
   }


   /** */
   private void deleteSourceFile(
      int infId,
      Long reqId,
      Path file,
      String reason
   )
   {
      try
      {
         Files.deleteIfExists(file);
      }
      catch( IOException e ) {
         log.warn (
            "MI_0600 source cleanup failed: infId={}, reqId={}, file={}, reason={}",
            infId, reqId, file.getFileName(), reason,
            e
         );
      }
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


}