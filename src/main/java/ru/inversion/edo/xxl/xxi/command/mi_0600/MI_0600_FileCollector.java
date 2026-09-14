package ru.inversion.edo.xxl.xxi.command.mi_0600;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class MI_0600_FileCollector
{
   /** Текущий пакет файлов. */
   public record FileBatch( List<Path> files, Instant startedAt ) {}

   /**
    * Время, когда впервые обнаружили непустой входной каталог.
    */
   private Instant batchStartedAt;


   /**
    * Проверяет, готов ли очередной набор файлов.
    * <p>
    * Логика:
    * 1. каталог пуст -> batch отсутствует;
    * 2. впервые появились файлы -> запоминаем T0;
    * 3. пока T0 + collectDelay не наступило -> ждём;
    * 4. после наступления -> фиксируем текущий список файлов.
    */
   public synchronized Optional<FileBatch> collect( Path directory, Duration collectDelay )
   {
      if( directory == null )
          throw new IllegalArgumentException("directory is null");

      if( collectDelay == null || collectDelay.isNegative() )
          throw new IllegalArgumentException("collectDelay is invalid");

      final List<Path> files = listFiles(directory);

      if( files.isEmpty() )
      {
         batchStartedAt = null;
         return Optional.empty();
      }

      final Instant now = Instant.now();

      /*
       * Первый файл текущего batch замечен.
       */
      if( batchStartedAt == null )
      {
          batchStartedAt = now;
          return Optional.empty();
      }

      /*
       * Окно накопления ещё не закончилось.
       */
      if( now.isBefore( batchStartedAt.plus(collectDelay) ) )
          return Optional.empty();

      FileBatch batch = new FileBatch( List.copyOf(files), batchStartedAt );

      /*
       * Batch передан следующему этапу.
       * Следующее обнаружение файлов начинает новое окно.
       */
      batchStartedAt = null;

      return Optional.of(batch);
   }


   /** */
   private List<Path> listFiles(Path directory)
   {
      if( !Files.isDirectory(directory) )
         throw new IllegalStateException( "MI_0600 input directory does not exist: " + directory );

      try( Stream<Path> stream = Files.list(directory) )
      {
         return stream.filter(Files::isRegularFile).sorted(Comparator.comparing(path -> path.getFileName().toString() )).toList();
      }
      catch( IOException e ) {
         throw new UncheckedIOException( "Failed to scan MI_0600 input directory: " + directory, e );
      }
   }
}