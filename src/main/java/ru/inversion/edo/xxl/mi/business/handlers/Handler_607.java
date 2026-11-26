package ru.inversion.edo.xxl.mi.business.handlers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;
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
import ru.inversion.edo.xxl.util.Attrs;
import ru.inversion.edo.xxl.xxi.command.mi_0600.MI_0606_Handler;
import ru.inversion.edo.xxl.xxi.protocol.XXLResponse;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
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
   private static final ZoneId DOCUMENT_RECEIVED_LOCAL_ZONE = ZoneId.of("Europe/Moscow");
   private static final ObjectReader DOCUMENT_READER = JsonMapper.builder()
         .addModule(new JavaTimeModule().addDeserializer(OffsetDateTime.class, new DocumentDateTimeDeserializer()))
         .build()
         .readerFor(DocumentReceived.class);

   private final Repository_607 repository;
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
           payload = DOCUMENT_READER.readValue(in);
      }
      catch(Exception failure) {
         Attrs context = Attrs.create(request.dump()).put("payload_error_type", failure.getClass().getSimpleName());
         if (failure instanceof JsonProcessingException json && json.getLocation() != null) {
            context.put("payload_line", json.getLocation().getLineNr());
            context.put("payload_column", json.getLocation().getColumnNr());
         }
         if (failure instanceof JsonMappingException mapping && !mapping.getPath().isEmpty())
            context.putIfNotNull("payload_field", mapping.getPath().getFirst().getFieldName());
         if (failure instanceof MismatchedInputException mismatch && mismatch.getTargetType() != null)
            context.put("payload_expected_type", mismatch.getTargetType().getSimpleName());

         throw Errors.miBusinessPayloadBadFormat("Не удалось разобрать DOCUMENT_RECEIVED JSON", failure, context.toMap());
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


      if( result.outfileId() != null )
          sendOutgoing606(infId, result.reqId(), result.outfileId());

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

         log.info (
            "Результат отправки 606: incoming_inf_id={}, incoming_req_id={}, outfile_id={}, req_id={}, result_code={}",
            infId, incomingReqId, outfileId, response.getParameters().get("req_id"), response.getResultCode()
         );
      }
      catch( RuntimeException failure )
      {
         Map<String, Object> context = new LinkedHashMap<>();
         if( failure instanceof XXLException exception)
             context.putAll(exception.getAttributes() );

         context.put( "incoming_inf_id", infId );
         context.put( "incoming_req_id", incomingReqId );
         context.put( "outfile_id",      outfileId );
         context.put( "call_uuid",       callUuid );

         XXLExceptionLogger.log( log, failure, "Ошибка отправки 606 после фиксации DOCUMENT_RECEIVED", context );
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
    * Интерпретирует ISO дату-время без смещения в зоне отправителя.
    */
   private static final class DocumentDateTimeDeserializer extends JsonDeserializer<OffsetDateTime>
   {
      @Override
      public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException
      {
         try {
            return InstantDeserializer.OFFSET_DATE_TIME.deserialize(parser, context);
         } catch (InvalidFormatException failure) {
            if (!(failure.getValue() instanceof String value))
               throw failure;

            LocalDateTime localDateTime;
            try {
               localDateTime = LocalDateTime.parse(value.strip());
            } catch (DateTimeParseException invalidLocalDateTime) {
               throw failure;
            }

            List<ZoneOffset> offsets = DOCUMENT_RECEIVED_LOCAL_ZONE.getRules().getValidOffsets(localDateTime);
            if (offsets.size() != 1)
               throw InvalidFormatException.from(parser, "No unique offset for local date-time in " + DOCUMENT_RECEIVED_LOCAL_ZONE,
                     value, OffsetDateTime.class);

            return localDateTime.atOffset(offsets.getFirst());
         }
      }
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
