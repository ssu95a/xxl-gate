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
import ru.inversion.edo.xxl.xxi.command.mi_0600.MI_0607_Repository;
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

   private final MI_0607_Repository repository;
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
          throw Errors.miBusinessPayloadBadFormat( "MI_0607 business request is null", Map.of() );

      if( request.payload() == null )
          throw Errors.miBusinessPayloadBadFormat( "DOCUMENT_RECEIVED payload отсутствует", request.dump() );

      MediaType mediaType = request.payload().mediaType();

      if( mediaType == null || !mediaType.isCompatibleWith(MediaType.APPLICATION_JSON) )
          throw Errors.miBusinessPayloadBadFormat( "DOCUMENT_RECEIVED должен иметь content-type application/json", request.dump() );

      final DocumentReceived payload;

      try( InputStream in = request.payload().openStream() ) {
         payload = objectMapper.readValue(in, DocumentReceived.class);
      }
      catch( Exception failure ) {
         throw Errors.miBusinessPayloadBadFormat( "Не удалось разобрать DOCUMENT_RECEIVED JSON", failure, request.dump() );
      }

      validate( payload, request );

      MI_0607_Repository.ReceiveResult result = repository.processDocumentReceived(
         payload.taskId().toString(),
         payload.docType(),
         payload.docDate(),
         payload.fileName(),
         payload.file()
      );

      /*
       * Ошибка штатно вернулась из нашего PL/SQL wrapper.
       * Это не DB_ERROR и не повод автоматически классифицировать
       * вызов как технический сбой XXI.
       */
      if(!result.success())
      {
         return MiBusinessResult.error (
                 request.requestId(),
                 Errors.ResultCode.XXI_CALL_FAILED,
                 result.retInfo(),
                 U.toMap(
                  "inf_id", INF_ID,
                  "task_id", payload.taskId(),
                  "file_name", payload.fileName(),
                  "base_id", result.baseId(),
                  "file_type", result.fileType(),
                  "parse_result", result.parseResult(),
                  "ret_code", result.retCode()
                 )
         );
      }


      return MiBusinessResult.success( request.requestId(), MiBusinessResult.CODE_SUCCESS, "DOCUMENT_RECEIVED обработан",
              Map.of(
                "base_id", result.baseId(),
                "file_type", result.fileType(),
                "parse_result", result.parseResult()
              ),
              Map.of(
                "task_id", payload.taskId(),
                "file_name", payload.fileName()
              )
      );
   }


   /** */
   private static void validate( DocumentReceived payload, MiBusinessRequest request )
   {
      if(payload == null)
         throw Errors.miBusinessPayloadBadFormat( "DOCUMENT_RECEIVED JSON пуст", request.dump() );

      if( !DOCUMENT_RECEIVED.equals(payload.type()) )
          throw Errors.miBusinessPayloadBadFormat( "Для inf_id=607 ожидается type=DOCUMENT_RECEIVED", U.toMap( "inf_id", INF_ID, "type", payload.type() ) );

      if(payload.taskId() == null)
         throw Errors.miBusinessPayloadBadFormat( "DOCUMENT_RECEIVED.taskId отсутствует", request.dump() );

      if( S.isNullOrEmpty(payload.docType() ))
         throw Errors.miBusinessPayloadBadFormat( "DOCUMENT_RECEIVED.docType отсутствует", request.dump() );

      if( payload.docDate() == null )
         throw Errors.miBusinessPayloadBadFormat( "DOCUMENT_RECEIVED.docDate отсутствует", request.dump() );

      if( S.isNullOrEmpty(payload.fileName() ) )
         throw Errors.miBusinessPayloadBadFormat( "DOCUMENT_RECEIVED.fileName отсутствует", request.dump() );

      if(payload.file() == null || payload.file().length == 0)
         throw Errors.miBusinessPayloadBadFormat( "DOCUMENT_RECEIVED.file отсутствует", request.dump() );
   }


   /**
    * Jackson сам декодирует JSON Base64 string -> byte[].
    * <p>
    * resolutionId/resolutions пока принимаются протоколом,
    * но текущая версия XXL их не обрабатывает.
    */
   @JsonIgnoreProperties(ignoreUnknown = true)
   private record DocumentReceived(
      String type,
      UUID taskId,
      String docType,
      OffsetDateTime docDate,
      String fileName,
      byte[] file
   )
   { }
}