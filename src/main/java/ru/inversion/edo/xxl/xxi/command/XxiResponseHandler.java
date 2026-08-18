package ru.inversion.edo.xxl.xxi.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.xxi.protocol.XXLRequest;
import ru.inversion.edo.xxl.xxi.protocol.XXLResponse;
import ru.inversion.edo.xxl.xxi.repo.PReq;
import ru.inversion.edo.xxl.xxi.repo.PRsp;
import ru.inversion.edo.xxl.xxi.repo.ReqRepository;
import ru.inversion.edo.xxl.xxi.repo.RspRepository;
import ru.inversion.utils.U;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class XxiResponseHandler
{
   private static final int STATUS_READY = 1;

   private final RspRepository rspRepository;
   private final ReqRepository reqRepository;

   public XXLResponse send( XXLRequest request )
   {
      PRsp rsp = rspRepository.getResponse(request.getResponseId());
      PReq req = reqRepository.getRequest(rsp.getRequestId());

      verifyIdentity( request, rsp, req );
      verifyReady(rsp);

      // следующий этап: сформировать и publish business response
      // затем MI_Response_Api.to_Sent(rsp_id)

   }

   private void verifyIdentity(
           XXLRequest request,
           PRsp rsp,
           PReq req
   )
   {
      Map<String, Object> mismatch = new LinkedHashMap<>();

      // mi_rsp
      if( !Objects.equals(rsp.getResponseId(), request.getResponseId()) )
      {
         mismatch.put("xml_rsp_id", request.getResponseId());
         mismatch.put("xxi_rsp_id", rsp.getResponseId());
      }

      if( !Objects.equals(rsp.getRequestId(), request.getRequestId()) )
      {
         mismatch.put("xml_req_id", request.getRequestId());
         mismatch.put("xxi_req_id", rsp.getRequestId());
      }

      if( !Objects.equals(rsp.getItemId(), request.getItemId()) )
      {
         mismatch.put("xml_itm_id", request.getItemId());
         mismatch.put("xxi_itm_id", rsp.getItemId());
      }

      if( !Objects.equals(rsp.getResponseUuid(), request.getExternalUuid()) )
      {
         mismatch.put("xml_external_uuid", request.getExternalUuid());
         mismatch.put("xxi_rsp_uuid", rsp.getResponseUuid());
      }


      // mi_req
      if( !Objects.equals(req.getRequestId(), request.getRequestId()) )
      {
         mismatch.put("xml_req_id", request.getRequestId());
         mismatch.put("xxi_req_id", req.getRequestId());
      }

      if( !Objects.equals(req.getInfId(), request.getInfId()) )
      {
         mismatch.put("xml_inf_id", request.getInfId());
         mismatch.put("xxi_inf_id", req.getInfId());
      }

      if( !Objects.equals(req.getCorrelationId(), request.getCorrelationId()) )
      {
         mismatch.put("xml_correlation_id", request.getCorrelationId());
         mismatch.put("xxi_correlation_id", req.getCorrelationId());
      }

      if( !Objects.equals(req.getOriginalRequestUuid(), request.getOriginalRequest()) )
      {
         mismatch.put("xml_original_request", request.getOriginalRequest());
         mismatch.put("xxi_original_request", req.getOriginalRequestUuid());
      }


      if( !mismatch.isEmpty() )
      {
         mismatch.put("rsp_id", request.getResponseId());
         mismatch.put("req_id", request.getRequestId());
         mismatch.put("itm_id", request.getItemId());
         mismatch.put("call_uuid", request.getCallUuid());

         throw Errors.requestMismatch(
                 request.getRequestId(),
                 mismatch
         );
      }
   }

   /** Response должен быть готов к отправке. */
   private void verifyReady(PRsp rsp)
   {
      if( rsp.getStatus() == null || rsp.getStatus() != STATUS_READY )
      {
         throw Errors.sendNotAllowed (
                 "Business response is not ready for send: rsp_id=" + rsp.getResponseId() + ", status_cd=" + rsp.getStatus(),
                 U.toMap(
                         "rsp_id",    rsp.getResponseId(),
                         "req_id",    rsp.getRequestId(),
                         "itm_id",    rsp.getItemId(),
                         "status_cd", rsp.getStatus()
                 )
         );
      }
   }
}