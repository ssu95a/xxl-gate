package ru.inversion.edo.xxl.xxi.command.mi_0600;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
   /** Зафиксированный набор файлов. */
   public record FileBatch( List<Path> files, Instant startedAt )
   {}


   /**
    * Состояние окна накопления конкретного inf_id.
    *
    * directory сохраняем, чтобы изменение FILE_SEND_DIR
    * начинало новое окно накопления.
    */
   private record CollectState( Path directory, Instant startedAt ) {}


   /**
    * У каждого исходящего inf_id собственное T0.
    */
   private final Map<Integer, CollectState> states = new HashMap<>();


   /**
    * Проверяет, готов ли очередной batch конкретного inf_id.
    *
    * Семантика:
    *
    * 1. каталог пуст -> состояние inf_id сбрасывается;
    * 2. впервые обнаружены файлы -> фиксируется T0;
    * 3. до T0 + collectDelay ждём;
    * 4. после наступления cutoff берём все файлы,
    *    присутствующие в каталоге на этот момент;
    * 5. состояние сбрасывается, следующий batch начнёт новое T0.
    */
   public synchronized Optional<FileBatch> collect(
           int infId,
           Path directory,
           Duration collectDelay
   )
   {
      if( infId <= 0 )
         throw new IllegalArgumentException("infId must be positive");

      if( directory == null )
         throw new IllegalArgumentException("directory is null");

      if( collectDelay == null || collectDelay.isNegative() )
         throw new IllegalArgumentException("collectDelay is invalid");


      final List<Path> files = listFiles(directory);

      /*
       * Пустой каталог завершает текущее окно.
       */
      if( files.isEmpty() )
      {
         states.remove(infId);
         return Optional.empty();
      }


      final Instant now = Instant.now();

      CollectState state = states.get(infId);

      /*
       * Первый файл для этого inf_id либо во время работы
       * изменился FILE_SEND_DIR.
       */
      if( state == null || !state.directory().equals(directory) )
      {
         state = new CollectState(directory, now);
         states.put(infId, state);
      }


      /*
       * Окно накопления ещё открыто.
       */
      if( now.isBefore(state.startedAt().plus(collectDelay)) )
         return Optional.empty();


      FileBatch batch = new FileBatch(
              List.copyOf(files),
              state.startedAt()
      );


      /*
       * Batch передан следующему этапу.
       *
       * Даже если дальнейшее сохранение в PG завершится ошибкой,
       * исходные файлы останутся на месте и на следующем scan
       * начнётся новое окно накопления.
       */
      states.remove(infId);

      return Optional.of(batch);
   }


   /**
    * Сбрасывает накопленное T0 конкретного inf_id.
    *
    * Используется, например, если вид сведений отключён
    * или перестал быть настроен на исходящий файловый обмен.
    */
   public synchronized void reset(int infId)
   {
      states.remove(infId);
   }


   /** */
   private List<Path> listFiles(Path directory)
   {
      if( !Files.isDirectory(directory) )
      {
         throw new IllegalStateException(
                 "MI_0600 input directory does not exist: " + directory
         );
      }

      try( Stream<Path> stream = Files.list(directory) )
      {
         return stream
                 .filter(Files::isRegularFile)
                 .sorted(
                         Comparator.comparing(
                                 path -> path.getFileName().toString()
                         )
                 )
                 .toList();
      }
      catch( IOException e )
      {
         throw new UncheckedIOException(
                 "Failed to scan MI_0600 input directory: " + directory,
                 e
         );
      }
   }
}