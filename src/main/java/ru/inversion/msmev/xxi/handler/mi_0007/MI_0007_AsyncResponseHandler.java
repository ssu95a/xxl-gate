package ru.inversion.msmev.xxi.handler.mi_0007;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.mi.response.MiAsyncResponse;
import ru.inversion.msmev.mi.response.MiAsyncResponseHandler;
import ru.inversion.msmev.mi.response.ProcessResult;
import ru.inversion.msmev.xxi.repo.ReqRepository;
import ru.inversion.utils.U;

/**
 * <b>Handler async-ответов для wsp_id=7.</b>
 * <p>
 * Зона ответственности:
 * - обработать container reject;
 * - обработать item response;
 * - вызвать соответствующий XXI API/handler;
 * - не вызывать to_Success напрямую, если это решает XXI handler.
 */
@Component
@RequiredArgsConstructor
public class MI_0007_AsyncResponseHandler implements MiAsyncResponseHandler {

   private final ReqRepository reqRepository;

   @Override
   public boolean supports(MiAsyncResponse response) {
      return response.wspId() != null && response.wspId() == 7;
   }

   @Override
   public ProcessResult handle( MiAsyncResponse response )
   {
      if( response.containerRejected() )
          return handleContainerRejected(response);

      if( response.itemResponse() )
         return handleItemResponse(response);

      throw Errors.miResponseBadFormat(
              "Unsupported async response kind",
              U.toMap( "kind", response.kind(), "req_id", response.reqId(), "wsp_id", response.wspId() )
      );
   }

   private ProcessResult handleContainerRejected(MiAsyncResponse response) {

      if( response.reqId() == null )
      {
         throw Errors.miResponseRequestNotFound (
                 "req_id is missing for container reject",
                 U.toMap (
                     "external_uuid", response.requestExternalUuid(),
                     "correlation_id",response.miCorrelationId()
                 )
         );
      }

      reqRepository.toError( response.reqId(), null );

      return ProcessResult.success (
           "MI_RESPONSE_CONTAINER_REJECT_APPLIED",
           "Container reject applied",
            U.toMap (
               "req_id", response.reqId(),
               "message_id", response.originalRequestId(),
               "response_code", response.responseCode()
           )
      );
   }

   private ProcessResult handleItemResponse(MiAsyncResponse response) {

      //responseApplyService.applyItemResponse(response);

      return ProcessResult.success(
              "MI_RESPONSE_ITEM_APPLIED",
              "Item response applied",
              U.toMap (
                "req_id", response.reqId(),
                "item_external_uuid", response.itemExternalUuid(),
                "message_id", response.originalRequestId(),
                "response_code", response.responseCode()
              )
      );
   }
}