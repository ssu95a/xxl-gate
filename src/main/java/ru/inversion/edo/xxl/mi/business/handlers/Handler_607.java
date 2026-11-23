package ru.inversion.edo.xxl.mi.business.handlers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequest;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequestHandler;
import ru.inversion.edo.xxl.mi.business.MiBusinessResult;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


/**
 * 607 / DOCUMENT_RECEIVED.
 */
@Component
@RequiredArgsConstructor
public class Handler_607 implements MiBusinessRequestHandler
{
   private static final int INF_ID = 607;

   private static final String DOCUMENT_RECEIVED = "DOCUMENT_RECEIVED";

   private final Repository_607 repository;
   private final ObjectMapper objectMapper;


   /** */
   @Override
   public Set<Integer> infIds()
   {
      return Set.of(INF_ID);
   }


   /** */
   @Override
   public MiBusinessResult handle(MiBusinessRequest request)
   {
      if( request == null )
          throw Errors.miBusinessPayloadBadFormat("MI_0607 business request is null", Map.of() );

      if(request.payload() == null)
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
            request.originalRequestId(),
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
                  "inf_id", INF_ID,
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
    * на текущий flow 607 они не влияют.
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
