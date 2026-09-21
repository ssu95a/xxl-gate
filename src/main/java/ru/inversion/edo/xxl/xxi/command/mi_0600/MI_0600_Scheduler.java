package ru.inversion.edo.xxl.xxi.command.mi_0600;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.xxi.repo.InfRole;
import ru.inversion.utils.U;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
            /*
             * Ошибка одного inf_id не должна останавливать обработку остальных видов сведений.
             */
            log.error( "MI_0600 file scan failed: infId={}", config.infId(), e );
         }
      }
   }


   /** */
   private void processInf( InfConfig config )
   {
      /*
       * Вид сведений временно отключен.
       * Старое T0 больше использовать нельзя.
       */
      if( !config.enabled() )
      {
         fileCollector.reset(config.infId());
         return;
      }

      /* WorkDir отсутствует - это критическая ошибка */
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
    *
    * После успешного createRequest() PostgreSQL является
    * durable source для дальнейшей отправки.
    */
   private void captureBatch( InfConfig config, MI_0600_FileCollector.FileBatch batch )
   {
      List<String> fileNames =
              batch.files()
                      .stream()
                      .map(path -> path.getFileName().toString())
                      .toList();

      Path zipPath = zipService.createZip(batch);

      final MI_0600_Repository.CreateResult created;

      try
      {
         /*
          * Зовем PG, где:
          *   создаем запрос - create mi_req + create mi_0600 item с бизнес данными
          *   сохраняем ZIP - конверт
          *   делаем COMMIT
          */
         created = repository.createRequest( config.infId(), zipPath, fileNames );

         /* Только после успешного COMMIT удаляем исходные файлы. */
         deleteSourceFiles( batch.files(), config.infId(), created.reqId() );

         /* Если стоит настройка копировать ZIP конверт в резервную папку и эта папка задана */
         if( config.makeZipCopy() && config.zipCopyDir() != null )
         {
             try {
                Files.copy( zipPath, config.zipCopyDir().resolve(zipPath.getFileName() ), REPLACE_EXISTING );
             }
             catch ( IOException e ) {
                  /* при создании копии не падаем, сообщаем как о не критичной ошибке */
                  log.warn( "Ошибка создания копии ZIP конверта из {} в папку {}, infId {} ", zipPath, config.zipCopyDir(), config.infId(), e );
             }
         }

      }
      finally {

         /*
          * Этот ZIP промежуточный:
          * после createRequest ZIP либо уже в PG,
          * либо операция создания request завершилась ошибкой.
          * Новый ZIP будет собран заново.
          */
         deleteTempZip(zipPath);
      }

      log.info (
           "MI_0600 batch captured: infId={}, reqId={}, itmId={}, filesCount={}",
           config.infId(),
           created.reqId(),
           created.itmId(),
           batch.files().size()
      );
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
}