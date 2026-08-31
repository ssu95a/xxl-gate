package ru.inversion.edo.xxl.xxi.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.transport.MiBusinessResponsePublisher;
import ru.inversion.edo.xxl.transport.MiPublisher;
import ru.inversion.edo.xxl.xxi.protocol.XXLRequest;
import ru.inversion.edo.xxl.xxi.protocol.XXLResponse;
import ru.inversion.edo.xxl.xxi.repo.*;
import ru.inversion.utils.U;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import ru.inversion.edo.xxl.transport.MiPublishReceipt;
import ru.inversion.edo.xxl.transport.XxlMiEnvelope;
import ru.inversion.edo.xxl.util.Attrs;
import ru.inversion.edo.xxl.xxi.repo.InfRepository;
import ru.inversion.edo.xxl.xxi.repo.PInf;


@Component
@RequiredArgsConstructor
public class XxiResponseHandler
{
   private static final int STATUS_READY = 1;

   private final RspRepository rspRepository;
   private final ReqRepository reqRepository;
   private final InfRepository infRepository;
   private final MiPublisher   miPublisher;


   /** */
   private void verifyIdentity( XXLRequest request, PRsp rsp, PReq req )
   {
      Map<String, Object> mismatch = new LinkedHashMap<>();

      // mi_rsp
      if( !Objects.equals( rsp.getResponseId(), request.getResponseId() ) )
      {
         mismatch.put("xml_rsp_id", request.getResponseId());
         mismatch.put("xxi_rsp_id", rsp.getResponseId()    );
      }

      if( !Objects.equals(rsp.getRequestId(), request.getRequestId()) )
      {
         mismatch.put("xml_req_id", request.getRequestId());
         mismatch.put("xxi_req_id", rsp.getRequestId()    );
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

         throw Errors.requestMismatch( request.getRequestId(), mismatch );
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

   public XXLResponse send( XXLRequest request ) {
      PRsp rsp = rspRepository.getResponse(request.getResponseId());
      PReq req = reqRepository.getRequest(rsp.getRequestId());

      verifyIdentity(request, rsp, req);
      verifyReady(rsp);

//      return XXLResponse.unsupportedOperation(
//              "MI business response publishing",
//              U.toMap(
//                      "rsp_id", rsp.getResponseId(),
//                      "req_id", rsp.getRequestId(),
//                      "itm_id", rsp.getItemId()
//              )
//      );

      PInf inf = infRepository.getInf(req.getInfId());


      //  Payload читаем только здесь
      String payload = rspRepository.getPayload( rsp.getResponseId() );

      XxlMiEnvelope envelope =
              XxlMiEnvelope.businessResponse()
                      .infNamespace(inf.getNamespace())
                      .ids(ids -> ids
                              .externalRequestUuid(rsp.getResponseUuid())
                              .messageId(rsp.getResponseUuid())
                              .originalRequestUuid(req.getOriginalRequestUuid())
                              .correlationId(req.getCorrelationId())
                              .reqId(req.getRequestId())
                              .infId(req.getInfId(), inf.getWspId())
                              .callUuid(request.getCallUuid())
                      )
                      .source(source -> source
                           .name(XxlMiEnvelope.DEFAULT_SOURCE_NAME)
                           .module("business-response")
                      )
                      .payload(p -> p.json(payload))
                      .build();

      MiPublishReceipt receipt =
              Objects.requireNonNull(
                      miPublisher.publishAsync(envelope),
                      "MI publisher returned null receipt"
              );


       // Только после успешной публикации.
       //
       //Если здесь ошибка, response остается READY.
       // Следующий запуск повторит publish с тем же rsp_uuid.
      try
      {
         rspRepository.toSent( rsp.getResponseId(), request.getCallUuid() );
      }
      catch( Exception e )
      {
         throw Errors.miPublishedStatusUpdateFailed(
              "Business response was published to MI, but response status was not changed to SENT",
              e,
              Attrs.merge (
                      receipt.toMap(),
                      U.toMap(
                              "rsp_id",    rsp.getResponseId(),
                              "req_id",    rsp.getRequestId(),
                              "itm_id",    rsp.getItemId(),
                              "call_uuid", request.getCallUuid(),
                              "published", true,
                              "to_Sent",   false
                      )
              )
         );
      }

      return XXLResponse.success()
        .action(request.getAction())
        .resultCode("SEND_PUBLISHED")
        .resultInfo("Business response published to MI")
        .parameter("rsp_id", rsp.getResponseId()).parameter("req_id", rsp.getRequestId()).parameter("itm_id", rsp.getItemId())
      .build();
   }
}