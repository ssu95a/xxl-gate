package ru.inversion.edo.xxl.xxi.command.mi_0600;

import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.utils.U;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class MI_0600_ZipService
{
   /** */
   public Path createZip( MI_0600_FileCollector.FileBatch batch )
   {
      if( batch == null )
          throw new IllegalArgumentException("batch is null");

      if( batch.files() == null || batch.files().isEmpty() )
          throw new IllegalArgumentException("batch is empty");

      Path zipPath = null;
      boolean completed = false;

      try
      {
         zipPath = Files.createTempFile("xxl_" + batch.infId() +"_", ".zip");

         writeZip(batch.files(), zipPath);

         completed = true;

         return zipPath;
      }
      catch( IOException e ) {
         throw Errors.payloadBuildFailed( "Ошибка создания ZIP архива", e, U.toMap("files_count", batch.files().size()) );
      }
      finally
      {
         if( !completed && zipPath != null )
         {
            try {
               Files.deleteIfExists(zipPath);
            }
            catch( IOException ignored ) { }
         }
      }
   }


   /** */
   private void writeZip( List<Path> files, Path zipPath ) throws IOException
   {
      try( ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath)) )
      {
         for( Path file : files )
              packFile( zip, file );
      }
   }


   /** */
   private void packFile( ZipOutputStream zip, Path file ) throws IOException
   {
      if( file == null || !Files.isRegularFile(file) )
          throw new IOException("Source file is unavailable");

      BasicFileAttributes before = Files.readAttributes(file, BasicFileAttributes.class);

      ZipEntry entry = new ZipEntry(file.getFileName().toString());

      zip.putNextEntry(entry);

      try {
         Files.copy(file, zip);
      }
      finally
      {
         zip.closeEntry();
      }

      /*
       * За время упаковки сторонний модуль не должен изменить файл.
       */
      BasicFileAttributes after = Files.readAttributes(file, BasicFileAttributes.class);

      if( before.size() != after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime()) )
          throw new IOException( "Source file was modified while creating ZIP" );
   }
}