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
import java.io.OutputStream;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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

   /**
    * Подготовленный входящий ZIP.
    *
    * zipPath:
    *   исходный ZIP, материализованный из MI payload.
    *
    * stagingDir:
    *   полностью распакованное содержимое ZIP,
    *   еще не опубликованное в RECEIVE_DIR.
    *
    * fileNames:
    *   имена файлов внутри ZIP для mi_0600_api.create_request.
    */
   public record PreparedZip(
      Path zipPath,
      Path stagingDir,
      List<String> fileNames,
      long totalSize
   )
   { }


   /**
    * Читает входящий payload, сохраняет исходный ZIP
    * во временный файл и полностью распаковывает его
    * в staging.
    *
    * RECEIVE_DIR при этом не изменяется.
    */
   public PreparedZip prepare( MiBusinessRequest request, Path receiveDir )
   {
      validateRequest(request);
      validateReceiveDir(receiveDir);

      Path normalizedReceiveDir = receiveDir.toAbsolutePath().normalize();

      Path zipPath = null;
      Path stagingDir = null;

      boolean completed = false;

      try
      {
         zipPath = createTempZip( normalizedReceiveDir, request.messageId() );

         copyPayload( request, zipPath );

         stagingDir = createStagingDir( normalizedReceiveDir, request.messageId() );

         PreparedZip prepared = extract( request, zipPath, stagingDir );

         completed = true;

         return prepared;
      }
      catch( ZipException e ) {
         throw Errors.miBusinessPayloadBadFormat( "Некорректный MI_0600 ZIP payload", e, request.dump() );
      }
      catch( IOException e ) {
         throw Errors.miBusinessRequestFailed( "Ошибка подготовки MI_0600 ZIP payload", e, request.dump() );
      }
      finally {

         if( !completed ) {
            deleteDirectory(stagingDir);
            deleteFile(zipPath);
         }
      }
   }


   /**
    * Публикует уже полностью распакованные файлы
    * из staging в RECEIVE_DIR.
    */
   public void publish( PreparedZip prepared, Path receiveDir )
   {
      if( prepared == null )
          throw new IllegalArgumentException( "Prepared ZIP is null" );

      validateReceiveDir(receiveDir);

      Path stagingDir = prepared.stagingDir();

      if( stagingDir == null || !Files.isDirectory(stagingDir) )
          throw Errors.miBusinessRequestFailed( "MI_0600 staging directory does not exist", null, null );

      Path normalizedReceiveDir = receiveDir.toAbsolutePath().normalize();

      try {
         createDirectories( stagingDir, normalizedReceiveDir );
         moveFiles        ( stagingDir, normalizedReceiveDir );
      }
      catch( IOException e ) {
         throw Errors.miBusinessRequestFailed( "Ошибка публикации файлов MI_0600 в RECEIVE_DIR", e, null );
      }
   }


   /**
    * Удаляет временный ZIP и staging.
    * <p>
    * Можно безопасно вызывать из finally.
    */
   public void cleanup( PreparedZip prepared )
   {
      if( prepared == null )
          return;

      deleteDirectory( prepared.stagingDir() );
      deleteFile( prepared.zipPath() );
   }


   /** */
   private void validateRequest( MiBusinessRequest request )
   {
      if( request == null )
          throw Errors.miBusinessPayloadBadFormat( "MI_0600 business request is null", null );

      MiBusinessPayload payload = request.payload();

      if( payload == null )
          throw Errors.miBusinessPayloadBadFormat( "MI_0600 payload is null", request.dump() );

      if( payload.size() <= 0 )
         throw Errors.miBusinessPayloadBadFormat( "MI_0600 ZIP payload is empty", request.dump() );


      MediaType mediaType = payload.mediaType();

      if( mediaType == null || !ZIP_MEDIA_TYPE.isCompatibleWith(mediaType) )
         throw Errors.miBusinessPayloadBadFormat( "Unsupported MI_0600 payload media type: " + payload.contentType(), request.dump() );
   }


   /** */
   private void validateReceiveDir( Path receiveDir )
   {
      if( receiveDir == null )
         throw Errors.config( "MI_0600 RECEIVE_DIR не настроена", null );


      if( !Files.exists(receiveDir) )
          throw Errors.config( "MI_0600 RECEIVE_DIR не существует: " + receiveDir, null );


      if( !Files.isDirectory(receiveDir) )
          throw Errors.config("MI_0600 RECEIVE_DIR не является директорией: " + receiveDir, null );
   }


   /**
    * Материализует исходный MI payload как настоящий ZIP-файл.
    *
    * Именно этот файл затем сохраняется
    * через mi_0600_api.create_request.
    */
   private Path createTempZip(
           Path receiveDir,
           UUID messageId
   )
           throws IOException
   {
      Path parent =
              receiveDir.getParent();

      if( parent == null )
      {
         throw new IOException(
                 "RECEIVE_DIR has no parent: "
                         + receiveDir
         );
      }


      return Files.createTempFile(
              parent,
              ".xxl_0600_"
                      + id(messageId)
                      + "_",
              ".zip"
      );
   }


   /** */
   private void copyPayload(
           MiBusinessRequest request,
           Path zipPath
   )
           throws IOException
   {
      try(
              InputStream input =
                      request
                              .payload()
                              .openStream();

              OutputStream output =
                      Files.newOutputStream(zipPath)
      )
      {
         input.transferTo(output);
      }


      long size =
              Files.size(zipPath);

      if( size <= 0 )
      {
         throw Errors.miBusinessPayloadBadFormat(
                 "MI_0600 ZIP payload is empty",
                 request.dump()
         );
      }
   }


   /**
    * Staging создаем рядом с RECEIVE_DIR,
    * чтобы последующие move оставались
    * на том же filesystem.
    */
   private Path createStagingDir(
           Path receiveDir,
           UUID messageId
   )
           throws IOException
   {
      Path parent =
              receiveDir.getParent();

      if( parent == null )
      {
         throw new IOException(
                 "RECEIVE_DIR has no parent: "
                         + receiveDir
         );
      }


      return Files.createTempDirectory(
              parent,
              ".xxl_0600_"
                      + id(messageId)
                      + "_"
      );
   }


   /** */
   private PreparedZip extract(
           MiBusinessRequest request,
           Path zipPath,
           Path stagingDir
   )
           throws IOException
   {
      List<String> fileNames =
              new ArrayList<>();

      Set<Path> extractedEntries =
              new HashSet<>();

      long totalSize = 0;


      try(
              InputStream source =
                      Files.newInputStream(zipPath);

              BufferedInputStream buffered =
                      new BufferedInputStream(source);

              ZipInputStream zip =
                      new ZipInputStream(buffered)
      )
      {
         ZipEntry entry;

         while(
                 (entry = zip.getNextEntry()) != null
         )
         {
            try
            {
               Path target =
                       resolveEntry(
                               request,
                               stagingDir,
                               entry
                       );


               /*
                * Один и тот же path внутри ZIP
                * дважды не принимаем.
                */
               if(
                       !extractedEntries.add(target)
               )
               {
                  throw Errors.miBusinessPayloadBadFormat(
                          "MI_0600 ZIP содержит повторяющийся entry: "
                                  + entry.getName(),
                          request.dump()
                  );
               }


               if( entry.isDirectory() )
               {
                  Files.createDirectories(
                          target
                  );

                  continue;
               }


               Path parent =
                       target.getParent();

               if( parent != null )
               {
                  Files.createDirectories(
                          parent
                  );
               }


               long size =
                       Files.copy(
                               zip,
                               target,
                               StandardCopyOption.REPLACE_EXISTING
                       );


               /*
                * В PG сохраняем имя относительно корня ZIP,
                * а не временный filesystem path.
                */
               String fileName =
                       stagingDir
                               .relativize(target)
                               .toString();


               fileNames.add(
                       fileName
               );

               totalSize += size;
            }
            finally
            {
               zip.closeEntry();
            }
         }
      }


      if( fileNames.isEmpty() )
      {
         throw Errors.miBusinessPayloadBadFormat(
                 "MI_0600 ZIP не содержит файлов",
                 request.dump()
         );
      }


      return new PreparedZip(
              zipPath,
              stagingDir,
              List.copyOf(fileNames),
              totalSize
      );
   }


   /**
    * Защита от ZIP Slip.
    */
   private Path resolveEntry(
           MiBusinessRequest request,
           Path stagingDir,
           ZipEntry entry
   )
   {
      String name =
              entry.getName();

      if(
              name == null ||
                      name.isBlank()
      )
      {
         throw Errors.miBusinessPayloadBadFormat(
                 "MI_0600 ZIP содержит entry без имени",
                 request.dump()
         );
      }


      Path target =
              stagingDir
                      .resolve(name)
                      .normalize();


      if(
              !target.startsWith(stagingDir)
      )
      {
         throw Errors.miBusinessPayloadBadFormat(
                 "MI_0600 ZIP entry выходит за пределы архива: "
                         + name,
                 request.dump()
         );
      }


      return target;
   }


   /**
    * Сначала создаем структуру каталогов
    * в RECEIVE_DIR.
    */
   private void createDirectories(
           Path stagingDir,
           Path receiveDir
   )
           throws IOException
   {
      try(
              Stream<Path> paths =
                      Files.walk(stagingDir)
      )
      {
         for(
                 Path source :
                 paths
                         .filter(Files::isDirectory)
                         .toList()
         )
         {
            if(
                    source.equals(stagingDir)
            )
            {
               continue;
            }


            Path relative =
                    stagingDir.relativize(
                            source
                    );


            Files.createDirectories(
                    receiveDir.resolve(relative)
            );
         }
      }
   }


   /**
    * После полной распаковки ZIP
    * переносим файлы из staging
    * в рабочую RECEIVE_DIR.
    */
   private void moveFiles(
           Path stagingDir,
           Path receiveDir
   )
           throws IOException
   {
      try(
              Stream<Path> paths =
                      Files.walk(stagingDir)
      )
      {
         for(
                 Path source :
                 paths
                         .filter(Files::isRegularFile)
                         .toList()
         )
         {
            Path relative =
                    stagingDir.relativize(
                            source
                    );


            Path target =
                    receiveDir
                            .resolve(relative)
                            .normalize();


            /*
             * Дополнительная защита.
             */
            if(
                    !target.startsWith(receiveDir)
            )
            {
               throw new IOException(
                       "Target file points outside RECEIVE_DIR: "
                               + relative
               );
            }


            moveFile(
                    source,
                    target
            );
         }
      }
   }


   /** */
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
      catch(
              AtomicMoveNotSupportedException e
      )
      {
         Files.move(
                 source,
                 target,
                 StandardCopyOption.REPLACE_EXISTING
         );
      }
   }


   /** */
   private void deleteFile(
           Path path
   )
   {
      if( path == null )
         return;


      try
      {
         Files.deleteIfExists(
                 path
         );
      }
      catch( IOException e )
      {
         log.warn(
                 "MI_0600 temporary ZIP cleanup failed: file={}",
                 path.getFileName(),
                 e
         );
      }
   }


   /** */
   private void deleteDirectory(
           Path directory
   )
   {
      if( directory == null )
         return;


      try
      {
         if(
                 !Files.exists(directory)
         )
         {
            return;
         }


         try(
                 Stream<Path> paths =
                         Files.walk(directory)
         )
         {
            for(
                    Path path :
                    paths
                            .sorted(
                                    Comparator.reverseOrder()
                            )
                            .toList()
            )
            {
               Files.deleteIfExists(
                       path
               );
            }
         }
      }
      catch( Exception e )
      {
         log.warn(
                 "MI_0600 temporary unpack directory cleanup failed: directory={}",
                 directory.getFileName(),
                 e
         );
      }
   }


   /** */
   private static String id(
           UUID messageId
   )
   {
      return messageId == null
              ? "unknown"
              : messageId.toString();
   }
}