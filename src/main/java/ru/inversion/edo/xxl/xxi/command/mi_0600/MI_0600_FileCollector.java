
package ru.inversion.edo.xxl.xxi.command.mi_0600;

import org.springframework.stereotype.Component;
import ru.inversion.utils.Checks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class MI_0600_FileCollector
{
   /** Зафиксированный набор файлов для упаковки в ZIP. */
   public record FileBatch( List<Path> files, Instant startedAt, int infId )
   {}

   /** Метаданные для обнаружения изменений файла между сканированиями. */
   private record FileState( long size, FileTime modifiedAt, Object fileKey )
   {}

   /** Состояние накопления для одного inf_id. */
   private record CollectState(
      Path directory,
      Map<Path, FileState> snapshot,
      Instant startedAt,
      Instant unchangedSince
   )
   {}

   private final Map<Integer, CollectState> states = new HashMap<>();

   /**
    * Возвращает batch, когда состав каталога и метаданные всех файлов
    * не менялись в течение collectDelay.
    *
    * startedAt в FileBatch остаётся временем первого обнаружения файлов.
    */
   public synchronized Optional<FileBatch> collect( int infId, Path directory, Duration collectDelay )
   {
      Checks.Numeric.positive( infId, "infId" );

      if( directory == null || !Files.isDirectory(directory) )
          throw new IllegalArgumentException("directory is null or not a directory");

      if( collectDelay == null || collectDelay.isNegative() )
          throw new IllegalArgumentException("collectDelay is invalid");

      List<Path> files = listFiles(directory);

      if( files.isEmpty() )
      {
         states.remove(infId);
         return Optional.empty();
      }

      Map<Path, FileState> snapshot = readSnapshot(files);
      Instant now = Instant.now();
      CollectState state = states.get(infId);

      if( state == null || !state.directory().equals(directory) )
      {
         states.put(infId, new CollectState(directory, snapshot, now, now));
         return Optional.empty();
      }

      if( !state.snapshot().equals(snapshot) )
      {
         states.put(infId, new CollectState(directory, snapshot, state.startedAt(), now));
         return Optional.empty();
      }

      if( now.isBefore(state.unchangedSince().plus(collectDelay)) )
         return Optional.empty();

      states.remove(infId);
      return Optional.of(new FileBatch(List.copyOf(files), state.startedAt(), infId));
   }

   /** Сбрасывает состояние, например если вид сведений отключён. */
   public synchronized void reset( int infId )
   {
      states.remove(infId);
   }

   /** Снимок состава и метаданных файлов. */
   private Map<Path, FileState> readSnapshot( List<Path> files )
   {
      Map<Path, FileState> result = new HashMap<>();

      for( Path file : files )
      {
         try
         {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);

            if( !attrs.isRegularFile() )
               throw new IOException("File is no longer regular: " + file);

            result.put(file, new FileState(attrs.size(), attrs.lastModifiedTime(), attrs.fileKey()));
         }
         catch( IOException e )
         {
            throw new UncheckedIOException("Failed to inspect MI_0600 input file: " + file, e);
         }
      }

      return Map.copyOf(result);
   }

   private List<Path> listFiles( Path directory )
   {
      try( Stream<Path> stream = Files.list(directory) )
      {
         return stream.filter(Files::isRegularFile)
                 .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                 .toList();
      }
      catch( IOException e )
      {
         throw new UncheckedIOException("Failed to scan MI_0600 input directory: " + directory, e);
      }
   }
}
