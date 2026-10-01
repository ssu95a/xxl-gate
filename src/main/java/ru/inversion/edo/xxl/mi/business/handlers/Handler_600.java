package ru.inversion.edo.xxl.mi.business.handlers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequest;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequestHandler;
import ru.inversion.edo.xxl.mi.business.MiBusinessResult;
import ru.inversion.edo.xxl.xxi.command.mi_0600.MI_0600_Repository;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;


/**
 * MI_0600.
 * <p>
 * Входящий файловый обмен:
 * <p>
 * MI
 *   -> XXL
 *   -> ZIP payload
 *   -> staging
 *   -> сохранение request + ZIP в XXI
 *   -> публикация файлов в RECEIVE_DIR
 */
@Component
@RequiredArgsConstructor
public class Handler_600 implements MiBusinessRequestHandler
{
   private static final Set<Integer> INF_IDS = Set.of( 602, 604 );

   private static final int CREATE_TYPE = 0;

   private final MI_0600_Repository repository;

   private final MI_0600_UnzipService unzipService;

   /** */
   @Override
   public Set<Integer> infIds()
   {
      return INF_IDS;
   }


   /** */
   @Override
   public MiBusinessResult handle( MiBusinessRequest request )
   {
      if( request == null )
         throw Errors.miBusinessPayloadBadFormat( "MI_0600 business request is null", Map.of() );

      Integer infId = request.infId();

      if( infId == null )
         throw Errors.miBusinessPayloadBadFormat( "MI_0600 infId is null", request.dump() );


      /*
       * RECEIVE_DIR задается отдельно для конкретного входящего inf_id.
       */
      Path receiveDir = repository.getReceiveDir( infId );


      /*
       * Читаем входящий ZIP и полностью распаковываем во временную staging-директорию.
       *
       * RECEIVE_DIR на этом этапе еще не меняется.
       */
      MI_0600_UnzipService.PreparedZip prepared = unzipService.prepare( request, receiveDir );


      try
      {
         /*
          * Сохраняем входящий request и исходный ZIP в XXI.
          *
          * После успешного createRequest PostgreSQL
          * является durable source исходного ZIP.
          */
         MI_0600_Repository.CreateResult created = repository.createRequest(
            infId,
            prepared.zipPath(),
            prepared.fileNames(),
            request.originalRequestId(),
            request.messageId(),
            null, // ctaxreq_id
            CREATE_TYPE
         );


         /*
          * Только после успешного COMMIT в XXI публикуем распакованные файлы в RECEIVE_DIR.
          */
         unzipService.publish( prepared, receiveDir );


         return MiBusinessResult.success(
                 request.requestId(),

                 MiBusinessResult.CODE_SUCCESS,

                 "MI_0600 files received",

                 Map.of(
                         "req_id",  created.reqId(),
                         "itm_id",  created.itmId(),
                         "files_count", prepared.fileNames().size()
                 ),

                 Map.of()
         );
      }
      finally
      {
         /*
          * Удаляем временный ZIP и staging.
          *
          * Если createRequest упал, исходный ZIP
          * еще не сохранен в PG, поэтому MI получит error
          * и сможет повторить доставку.
          *
          * Если createRequest завершился успешно,
          * ZIP уже хранится в XXI.
          */
         unzipService.cleanup(prepared);
      }
   }
}