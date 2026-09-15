package ru.inversion.edo.xxl.xxi.command.mi_0600;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;


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
      taskRegistrar.addTriggerTask(
              this::run,
              context ->
              {
                 Duration scanDelay = repository.getScanDelay();

                 if( scanDelay == null || scanDelay.isZero() || scanDelay.isNegative() ) {
                    throw new IllegalStateException( "MI_0600 scanDelay must be positive" );
                 }

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
      /*
       * Capture новых файлов и отправка уже созданных request —
       * независимые операции.
       */
      captureFiles();
      sendPending();
   }


   /**
    * Ищет новые файлы по всем inf_id WSP 600.
    */
   private void captureFiles()
   {
      final List<InfConfig> configs;

      try
      {
         configs = repository.loadInfConfigs();
      }
      catch( Exception e )
      {
         log.error(
                 "MI_0600 failed to load inf configuration",
                 e
         );

         return;
      }


      for( InfConfig config : configs )
      {
         try
         {
            processInf(config);
         }
         catch( Exception e )
         {
            /*
             * Ошибка одного inf_id не должна останавливать
             * обработку остальных видов сведений.
             */
            log.error(
                    "MI_0600 file scan failed: infId={}",
                    config.infId(),
                    e
            );
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


      /*
       * sendDir отсутствует — это не исходящий вид сведений.
       * Например, данный inf_id только принимает ZIP из MI.
       */
      if( config.workDir() == null )
      {
         fileCollector.reset(config.infId());
         return;
      }


      fileCollector.collect( config.infId(), config.workDir(), config.collectDelay() ).ifPresent(batch -> captureBatch(config, batch) );
   }


   /**
    * Фиксирует snapshot файлов в PostgreSQL.
    *
    * После успешного createRequest() PostgreSQL является
    * durable source для дальнейшей отправки.
    */
   private void captureBatch(
           InfConfig config,
           MI_0600_FileCollector.FileBatch batch
   )
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
          * PG:
          *   create mi_req
          *   create mi_0600 item
          *   persist ZIP
          *   COMMIT
          */
         created =
                 repository.createRequest(
                         config.infId(),
                         zipPath,
                         fileNames
                 );
      }
      finally
      {
         /*
          * Этот ZIP только промежуточный:
          * после createRequest ZIP либо уже durable в PG,
          * либо операция создания request завершилась ошибкой.
          */
         deleteTempZip(zipPath);
      }


      /*
       * Только после успешного COMMIT удаляем исходные файлы.
       */
      deleteSourceFiles(
              batch.files(),
              config.infId(),
              created.reqId()
      );


      log.info(
              "MI_0600 batch captured: infId={}, reqId={}, itmId={}, filesCount={}",
              config.infId(),
              created.reqId(),
              created.itmId(),
              batch.files().size()
      );
   }


   /**
    * Отправляет накопленные в PG request, которым сейчас
    * разрешена отправка.
    *
    * Производственный календарь, выходные, праздники,
    * временные окна и выбор req_id полностью находятся в PG.
    */
   private void sendPending()
   {
      try
      {
         repository.sendPending();
      }
      catch( Exception e )
      {
         /*
          * Capture уже выполненных batch от этого не откатывается.
          * ZIP остаются в PG и будут рассмотрены следующим scan.
          */
         log.error(
                 "MI_0600 send pending failed",
                 e
         );
      }
   }


   /** */
   private void deleteSourceFiles(
           List<Path> files,
           long infId,
           long reqId
   )
   {
      for( Path file : files )
      {
         try
         {
            Files.deleteIfExists(file);
         }
         catch( IOException e )
         {
            log.error(
                    "MI_0600 source file cleanup failed: infId={}, reqId={}, file={}",
                    infId,
                    reqId,
                    file.getFileName(),
                    e
            );
         }
      }
   }


   /** */
   private void deleteTempZip(Path zipPath)
   {
      if( zipPath == null )
         return;

      try
      {
         Files.deleteIfExists(zipPath);
      }
      catch( IOException e )
      {
         log.warn(
                 "MI_0600 temporary ZIP cleanup failed: file={}",
                 zipPath.getFileName(),
                 e
         );
      }
   }
}