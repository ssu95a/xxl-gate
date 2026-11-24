package ru.inversion.edo.xxl.mi.business.handlers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.error.XXLException;
import ru.inversion.edo.xxl.error.XXLExceptionLogger;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequest;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequestHandler;
import ru.inversion.edo.xxl.mi.business.MiBusinessResult;
import ru.inversion.edo.xxl.xxi.command.mi_0600.MI_0606_Handler;
import ru.inversion.edo.xxl.xxi.protocol.XXLResponse;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


/**
 * 607, 608 / DOCUMENT_RECEIVED.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Handler_607 implements MiBusinessRequestHandler
{
   private static final Set<Integer> INF_IDS = Set.of(607, 608);

   private final Repository_607 repository;
   private final ObjectMapper objectMapper;
   private final MI_0606_Handler outgoing606;


   /** */
   @Override
   public Set<Integer> infIds()
   {
      return INF_IDS;
   }


   /** */
   @Override
   public MiBusinessResult handle(MiBusinessRequest request)
   {
      if( request == null )
          throw Errors.miBusinessPayloadBadFormat("DOCUMENT_RECEIVED business request is null", Map.of() );

      Integer infId = request.infId();
      if( infId == null || !INF_IDS.contains(infId) )
          throw Errors.miBusinessPayloadBadFormat("Недопустимый вид сведений для DOCUMENT_RECEIVED", request.dump() );

      if( request.payload() == null)
          throw Errors.miBusinessPayloadBadFormat("DOCUMENT_RECEIVED payload отсутствует", request.dump() );

      MediaType mediaType = request.payload().mediaType();

      if( mediaType == null || !mediaType.isCompatibleWith(MediaType.APPLICATION_JSON))
          throw Errors.miBusinessPayloadBadFormat("DOCUMENT_RECEIVED должен иметь content-type application/json", request.dump() );

      final DocumentReceived payload;

      try( InputStream in = request.payload().openStream()) {
           payload = objectMapper.readValue( in, DocumentReceived.class );
      }
      catch(Exception failure) {
         throw Errors.miBusinessPayloadBadFormat("Не удалось разобрать DOCUMENT_RECEIVED JSON", failure, request.dump() );
      }

      validate( payload, request );

      /*
       * Один вызов Repository:
       *
       * MI_REQ
       * MI_0600
       * MI_0600_F
       * RSD_BASES
       * RD_PKG.Parsing
       *
       * выполняются внутри одного DB-call.
       */
      Repository_607.ReceiveResult result =
        repository.processDocumentReceived(
            infId,
            request.requestId(),
            request.messageId(),
            request.correlationId(),

            payload.taskId(),
            payload.docType(),
            payload.docDate(),

            payload.fileName(),
            payload.file()
        );


      // Штатные отказы завершают обработку без повтора доставки.
      if(!result.success())
      {
         return MiBusinessResult.error(
                 request.requestId(),
                 result.duplicate() ? Repository_607.DUPLICATE_REQUEST : Errors.ResultCode.XXI_CALL_FAILED,
                 result.retInfo(),
                 U.toMap(
                  "inf_id", infId,
                  "req_id", result.reqId(),
                  "itm_id", result.itmId(),
                  "task_id", payload.taskId(),
                  "file_name", payload.fileName(),
                  "base_id", result.baseId(),
                  "file_type", result.fileType(),
                  "parse_result", result.parseResult(),
                  "ret_code", result.retCode()
                 )
         );
      }


      sendOutgoing606(infId, result.reqId(), result.parseResult());

      return MiBusinessResult.success(
              request.requestId(),
              MiBusinessResult.CODE_SUCCESS,
              "DOCUMENT_RECEIVED обработан",
              Map.of(
                  "req_id", result.reqId(), "itm_id", result.itmId(),
                  "base_id", result.baseId(),
                  "file_type", result.fileType(),
                  "parse_result", result.parseResult()
              ),
              Map.of(
                   "task_id", payload.taskId(),
                   "file_name", payload.fileName(),
                   "idempotent", false
              )
      );
   }


   private void sendOutgoing606(int infId, long incomingReqId, long outfileId)
   {
      UUID callUuid = UUID.randomUUID();
      try {
         XXLResponse response = outgoing606.sendDocument(outfileId, callUuid);
         log.info("Результат отправки 606: incoming_inf_id={}, incoming_req_id={}, outfile_id={}, req_id={}, result_code={}",
               infId, incomingReqId, outfileId, response.getParameters().get("req_id"), response.getResultCode());
      } catch (RuntimeException failure) {
         Map<String, Object> context = new LinkedHashMap<>();
         if (failure instanceof XXLException exception)
            context.putAll(exception.getAttributes());
         context.put("incoming_inf_id", infId);
         context.put("incoming_req_id", incomingReqId);
         context.put("outfile_id", outfileId);
         context.put("call_uuid", callUuid);
         XXLExceptionLogger.log(log, failure, "Ошибка отправки 606 после фиксации DOCUMENT_RECEIVED", context);
      }
   }

   /** */
   private static void validate( DocumentReceived payload, MiBusinessRequest request )
   {
      if( payload == null )
          throw Errors.miBusinessPayloadBadFormat( "DOCUMENT_RECEIVED JSON пуст", request.dump() );

      if( payload.taskId() == null )
          throw Errors.miBusinessPayloadBadFormat( "DOCUMENT_RECEIVED.taskId отсутствует", request.dump() );

      if( S.isNullOrEmpty(payload.docType()) )
          throw Errors.miBusinessPayloadBadFormat( "DOCUMENT_RECEIVED.docType отсутствует", request.dump() );

      if( payload.docDate() == null )
          throw Errors.miBusinessPayloadBadFormat( "DOCUMENT_RECEIVED.docDate отсутствует", request.dump() );

      if( payload.responseDateTime() == null )
         throw Errors.miBusinessPayloadBadFormat( "DOCUMENT_RECEIVED.responseDateTime отсутствует", request.dump() );

      if( S.isNullOrEmpty(payload.fileName()) )
          throw Errors.miBusinessPayloadBadFormat( "DOCUMENT_RECEIVED.fileName отсутствует", request.dump() );

      if( payload.file() == null || payload.file().length == 0)
          throw Errors.miBusinessPayloadBadFormat( "DOCUMENT_RECEIVED.file отсутствует", request.dump() );
   }


   /**
    * Jackson сам декодирует Base64 -> byte[].
    * <p>
    * resolutionId / resolutions пока игнорируются:
    * они не участвуют в обработке DOCUMENT_RECEIVED.
    */
   @JsonIgnoreProperties(ignoreUnknown = true)
   private record DocumentReceived(
      UUID taskId,
      String docType,
      OffsetDateTime docDate,
      String fileName,
      byte[] file,
      OffsetDateTime responseDateTime
   )
   { }
}
