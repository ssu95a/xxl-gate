package ru.inversion.edo.xxl.mi.business.handlers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import ru.inversion.edo.xxl.mi.business.MiBusinessRequest;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequestHandler;
import ru.inversion.edo.xxl.mi.business.MiBusinessResult;

import java.nio.file.Path;
import java.util.Set;


/**
 * MI_0600.
 *
 * Входящий файловый обмен:
 *
 * MI
 *   -> XXL
 *   -> ZIP payload
 *   -> распаковка в RECEIVE_DIR
 *   -> регистрация результата в XXI
 */
@Component
@RequiredArgsConstructor
public class Handler_600 implements MiBusinessRequestHandler
{
   private final Repository_600 repository;

   private final MI_0600_UnzipService unzipService;


   /** */
   @Override
   public Set<Integer> infIds()
   {
      return repository.infIds();
   }


   /** */
   @Override
   public MiBusinessResult handle( MiBusinessRequest request )
   {
      /*
       * request уже прошел общий transport/parser слой.
       * Для MI_0600 payload является ZIP, его содержимое в PG не передается.
       */

      int infId = request.infId();

      /*
       * RECEIVE_DIR является настройкой конкретного вида сведений.
       */
      Path receiveDir = repository.getReceiveDir(infId);

      /*
       * Полностью распаковываем ZIP через staging.
       *
       * В RECEIVE_DIR файлы появляются только после того,
       * как весь ZIP успешно прочитан.
       */
      MI_0600_UnzipService.ExtractResult extracted = unzipService.extract( request, receiveDir );

      /*
       * Только после успешной публикации файлов
       * фиксируем обработку запроса в XXI.
       */
      return repository.apply(request);
   }
}