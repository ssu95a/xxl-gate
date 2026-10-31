package ru.inversion.edo.xxl.xxi.command.mi_0600;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.xxi.repo.InfRole;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MI_0600_ProcessedCleaner
{
   private static final String PROCESSED_DIR = ".processed";

   private final MI_0600_Repository repository;

   /**
    * Очистка локальной истории сформированных ZIP.
    *
    * По умолчанию раз в сутки в 03:00.
    */
   @Scheduled(
           cron = "${xxl.mi-0600.processed-cleaner.cron:0 0 3 * * *}"
   )
   public void clean()
   {
      final List<InfConfig> configs;

      try
      {
         configs = repository.loadInfConfigs(InfRole.Initiator);
      }
      catch( Exception e )
      {
         log.warn(
                 "MI_0600 processed cleanup: failed to load configuration",
                 e
         );

         return;
      }

      for( InfConfig config : configs )
      {
         try
         {
            clean(config);
         }
         catch( Exception e )
         {
            log.warn(
                    "MI_0600 processed cleanup failed: infId={}",
                    config.infId(),
                    e
            );
         }
      }
   }


   private void clean(InfConfig config) throws IOException
   {
      Path workDir = config.workDir();

      if( workDir == null )
         return;

      Path processedDir = workDir.resolve(PROCESSED_DIR);

      /*
       * Не идём по symbolic link.
       */
      if( !Files.isDirectory(
              processedDir,
              LinkOption.NOFOLLOW_LINKS
      ) )
      {
         return;
      }

      int keepDays = config.getProcessedKeepDays();

      /*
       * <= 0 означает "не чистить".
       */
      if( keepDays <= 0 )
         return;

      Instant cutoff =
              Instant.now().minus(Duration.ofDays(keepDays));

      long deletedCount = 0;
      long deletedBytes = 0;

      try( DirectoryStream<Path> files =
                   Files.newDirectoryStream(processedDir) )
      {
         for( Path file : files )
         {
            if( !isManagedZip(config.infId(), file) )
               continue;

            BasicFileAttributes attributes;

            try
            {
               attributes = Files.readAttributes(
                       file,
                       BasicFileAttributes.class,
                       LinkOption.NOFOLLOW_LINKS
               );
            }
            catch( IOException e )
            {
               log.warn(
                       "MI_0600 processed cleanup: failed to inspect file: infId={}, file={}",
                       config.infId(),
                       file.getFileName(),
                       e
               );

               continue;
            }

            if( !attributes.isRegularFile() )
               continue;

            Instant modifiedAt =
                    attributes.lastModifiedTime().toInstant();

            if( !modifiedAt.isBefore(cutoff) )
               continue;

            long size = attributes.size();

            try
            {
               Files.delete(file);

               deletedCount++;
               deletedBytes += size;
            }
            catch( IOException e )
            {
               log.warn(
                       "MI_0600 processed cleanup: failed to delete file: infId={}, file={}",
                       config.infId(),
                       file.getFileName(),
                       e
               );
            }
         }
      }

      /*
       * Не шумим логом каждый день, если чистить нечего.
       */
      if( deletedCount > 0 )
      {
         log.info(
                 "MI_0600 processed cleanup completed: infId={}, keepDays={}, deletedFiles={}, deletedBytes={}",
                 config.infId(),
                 keepDays,
                 deletedCount,
                 deletedBytes
         );
      }
   }


   /**
    * Cleaner имеет право удалять только ZIP,
    * созданные нашим MI_0600_ZipService для конкретного infId.
    */
   private boolean isManagedZip(int infId, Path file)
   {
      String name = file.getFileName().toString();
      return name.startsWith("xxl_" + infId + "_") && name.endsWith(".zip");
   }
}