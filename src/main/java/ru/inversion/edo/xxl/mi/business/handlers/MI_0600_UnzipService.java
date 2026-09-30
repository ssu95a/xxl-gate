package ru.inversion.edo.xxl.mi.business.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.mi.business.MiBusinessPayload;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequest;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;


@Component
@Slf4j
public class MI_0600_UnzipService
{
   private static final MediaType ZIP_MEDIA_TYPE = MediaType.parseMediaType("application/zip");


   public record ExtractResult( int filesCount, long totalSize ) { }


   /**
    * Распаковывает входящий ZIP во временную директорию,
    * после успешной распаковки публикует файлы в receiveDir.
    */
   public ExtractResult extract( MiBusinessRequest request, Path receiveDir )
   {
      validateRequest(request);
      validateReceiveDir(receiveDir);

      final Path normalizedReceiveDir = receiveDir.toAbsolutePath().normalize();

      Path stagingDir = null;

      try
      {
         stagingDir = createStagingDir( normalizedReceiveDir, request.messageId() );

         ExtractResult result = extractToStaging( request, stagingDir );

         publish( stagingDir, normalizedReceiveDir );

         return result;
      }
      catch( ZipException e )
      {
         throw Errors.miBusinessPayloadBadFormat(
                 "Некорректный MI_0600 ZIP payload",
                 e,
                 request.dump()
         );
      }
      catch( IOException e )
      {
         throw Errors.miBusinessRequestFailed(
                 "Ошибка обработки MI_0600 ZIP payload",
                 e,
                 request.dump()
         );
      }
      finally
      {
         deleteStagingDir(stagingDir);
      }
   }


   /** */
   private void validateRequest(
           MiBusinessRequest request
   )
   {
      if( request == null )
      {
         throw Errors.miBusinessPayloadBadFormat(
                 "MI_0600 business request is null",
                 null
         );
      }

      MiBusinessPayload payload = request.payload();

      if( payload == null )
      {
         throw Errors.miBusinessPayloadBadFormat(
                 "MI_0600 payload is null",
                 request.dump()
         );
      }

      if( payload.size() <= 0 )
      {
         throw Errors.miBusinessPayloadBadFormat(
                 "MI_0600 ZIP payload is empty",
                 request.dump()
         );
      }

      MediaType mediaType = payload.mediaType();

      if(
              mediaType == null ||
                      !ZIP_MEDIA_TYPE.isCompatibleWith(mediaType)
      )
      {
         throw Errors.miBusinessPayloadBadFormat(
                 "Unsupported MI_0600 payload media type: "
                         + payload.contentType(),
                 request.dump()
         );
      }
   }


   /** */
   private void validateReceiveDir(
           Path receiveDir
   )
   {
      if( receiveDir == null )
      {
         throw Errors.config(
                 "MI_0600 RECEIVE_DIR не настроена",
                 null
         );
      }

      if( !Files.exists(receiveDir) )
      {
         throw Errors.config(
                 "MI_0600 RECEIVE_DIR не существует",
                 null
         );
      }

      if( !Files.isDirectory(receiveDir) )
      {
         throw Errors.config(
                 "MI_0600 RECEIVE_DIR не является директорией",
                 null
         );
      }
   }


   /**
    * Staging создается рядом с RECEIVE_DIR,
    * а не внутри нее.
    *
    * Поэтому потребитель RECEIVE_DIR не увидит
    * наполовину распакованный архив.
    */
   private Path createStagingDir(
           Path receiveDir,
           UUID messageId
   )
           throws IOException
   {
      Path parent = receiveDir.getParent();

      if( parent == null )
      {
         throw new IOException(
                 "RECEIVE_DIR has no parent: " + receiveDir
         );
      }

      String id =
              messageId == null
                      ? "unknown"
                      : messageId.toString();

      return Files.createTempDirectory(
              parent,
              ".xxl_0600_" + id + "_"
      );
   }


   /** */
   private ExtractResult extractToStaging(
           MiBusinessRequest request,
           Path stagingDir
   )
           throws IOException
   {
      int filesCount = 0;
      long totalSize = 0;

      Set<Path> extractedEntries =
              new HashSet<>();

      try(
              InputStream source =
                      request.payload().openStream();

              BufferedInputStream buffered =
                      new BufferedInputStream(source);

              ZipInputStream zip =
                      new ZipInputStream(buffered)
      )
      {
         ZipEntry entry;

         while( (entry = zip.getNextEntry()) != null )
         {
            try
            {
               Path target =
                       resolveEntry(
                               stagingDir,
                               entry
                       );

               if( !extractedEntries.add(target) )
               {
                  throw Errors.miBusinessPayloadBadFormat(
                          "MI_0600 ZIP содержит повторяющийся entry: "
                                  + entry.getName(),
                          request.dump()
                  );
               }

               if( entry.isDirectory() )
               {
                  Files.createDirectories(target);
                  continue;
               }

               Path parent = target.getParent();

               if( parent != null )
                  Files.createDirectories(parent);

               long size =
                       Files.copy(
                               zip,
                               target,
                               StandardCopyOption.REPLACE_EXISTING
                       );

               filesCount++;
               totalSize += size;
            }
            finally
            {
               zip.closeEntry();
            }
         }
      }

      if( filesCount == 0 )
      {
         throw Errors.miBusinessPayloadBadFormat(
                 "MI_0600 ZIP не содержит файлов",
                 request.dump()
         );
      }

      return new ExtractResult(
              filesCount,
              totalSize
      );
   }


   /**
    * Защита от ZIP Slip:
    *
    * ../../some/file
    *
    * не должен позволить выйти из stagingDir.
    */
   private Path resolveEntry(
           Path stagingDir,
           ZipEntry entry
   )
   {
      String name = entry.getName();

      if( name == null || name.isBlank() )
      {
         throw Errors.miBusinessPayloadBadFormat(
                 "MI_0600 ZIP содержит entry без имени",
                 null
         );
      }

      Path target =
              stagingDir
                      .resolve(name)
                      .normalize();

      if( !target.startsWith(stagingDir) )
      {
         throw Errors.miBusinessPayloadBadFormat(
                 "MI_0600 ZIP entry выходит за пределы целевой директории: "
                         + name,
                 null
         );
      }

      return target;
   }


   /**
    * ZIP к этому моменту распакован полностью.
    *
    * Теперь переносим содержимое staging в RECEIVE_DIR.
    */
   private void publish(
           Path stagingDir,
           Path receiveDir
   )
           throws IOException
   {
      /*
       * Сначала создаем структуру каталогов.
       */
      try(
              Stream<Path> paths =
                      Files.walk(stagingDir)
      )
      {
         for( Path source :
                 paths.filter(Files::isDirectory).toList() )
         {
            if( source.equals(stagingDir) )
               continue;

            Path relative =
                    stagingDir.relativize(source);

            Files.createDirectories(
                    receiveDir.resolve(relative)
            );
         }
      }


      /*
       * После успешной распаковки публикуем файлы.
       */
      try(
              Stream<Path> paths =
                      Files.walk(stagingDir)
      )
      {
         for( Path source :
                 paths.filter(Files::isRegularFile).toList() )
         {
            Path relative =
                    stagingDir.relativize(source);

            Path target =
                    receiveDir
                            .resolve(relative)
                            .normalize();

            moveFile(source, target);
         }
      }
   }


   /**
    * Staging создается на том же filesystem,
    * поэтому сначала пытаемся сделать atomic move.
    */
   private void moveFile(
           Path source,
           Path target
   )
           throws IOException
   {
      try
      {
         Files.move(
                 source,
                 target,
                 StandardCopyOption.ATOMIC_MOVE,
                 StandardCopyOption.REPLACE_EXISTING
         );
      }
      catch( AtomicMoveNotSupportedException e )
      {
         Files.move(
                 source,
                 target,
                 StandardCopyOption.REPLACE_EXISTING
         );
      }
   }


   /** */
   private void deleteStagingDir(
           Path stagingDir
   )
   {
      if( stagingDir == null )
         return;

      try
      {
         if( !Files.exists(stagingDir) )
            return;

         try(
                 Stream<Path> paths =
                         Files.walk(stagingDir)
         )
         {
            for(
                    Path path :
                    paths
                            .sorted(
                                    (a, b) ->
                                            b.getNameCount()
                                                    - a.getNameCount()
                            )
                            .toList()
            )
            {
               Files.deleteIfExists(path);
            }
         }
      }
      catch( Exception e )
      {
         log.warn(
                 "MI_0600 temporary unpack directory cleanup failed: directory={}",
                 stagingDir.getFileName(),
                 e
         );
      }
   }
}