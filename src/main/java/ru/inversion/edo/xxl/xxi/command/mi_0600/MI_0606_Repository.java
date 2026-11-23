package ru.inversion.edo.xxl.xxi.command.mi_0600;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.transport.PayloadDto;
import ru.inversion.edo.xxl.util.Attrs;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.net.URL;
import java.sql.Blob;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * SEND_DOCUMENT
 * готовит и отправляет документ созданный бизнес-модулем
 */
@Repository
@RequiredArgsConstructor
public class MI_0606_Repository
{
   private static final String MEDIA_TYPE = MediaType.APPLICATION_JSON_VALUE;

   private static final URL DEF_XML = MI_0606_Repository.class.getResource("plsql/def.xml");

   private static final String PREPARE_SEND_DOCUMENT = "MI_0606.prepare_Send_Document";

   private static final int RET_OK = 0;

   private final XxiRepositoryExecutor db;
   private final ObjectMapper objectMapper;

   /** */
   public PayloadDto prepareSendDocument(long reqId)
   {
      SendDocumentData data = loadSendDocument(reqId);

      try
      {
         byte[] payload =
            objectMapper.writeValueAsBytes(
                new SendDocumentPayload(
                  "SEND_DOCUMENT",

                  data.taskId(),
                  data.docKey(),

                  data.docType(),
                  data.errorCode(),

                  data.fileName(),

                  Base64.getEncoder().encodeToString(data.content())
                )
            );

         return new PayloadDto( MEDIA_TYPE, payload, payload.length );

      }
      catch( JsonProcessingException e ) {
         throw Errors.payloadBuildFailed( "Не удалось сформировать SEND_DOCUMENT", e, U.toMap( "req_id", reqId, "task_id", data.taskId(), "doc_key", data.docKey() ) );
      }
   }


   /** */
   private SendDocumentData loadSendDocument(long reqId)
   {
      return db.execute( PREPARE_SEND_DOCUMENT, U.toMap( "req_id", reqId ),tc -> loadSendDocument( tc, reqId ) );
   }


   /** */
   private static SendDocumentData loadSendDocument( TaskContext tc, long reqId )
      throws Exception
   {
      ParametersByName args = Attrs.create().put("req_id",reqId).toParameters();

      try (
         IDataCall call =
            SQLCallBuilder
               .NEW(tc)
               .url(DEF_XML)
               .name(PREPARE_SEND_DOCUMENT)
               .callBackParameters(args)
            .build()
               .execute()
      )
      {
         Integer retCode = call.getReturnValue();
         String retInfo  = call.get("ret_info");

         if( retCode == null )
             throw Errors.xxiCallFailed( PREPARE_SEND_DOCUMENT, reqId, -1, "out parameter '_return' is null", null );

         /*
          * prepare_Send_Document не имеет отрицательного business-result.
          *
          * Если request уже дошёл до send-handler, его journal обязан
          * позволять полностью восстановить SEND_DOCUMENT.
          */
         if( retCode != RET_OK )
             throw Errors.xxiCallFailed( PREPARE_SEND_DOCUMENT, reqId, retCode, retInfo, null );

         String taskId    = call.get("task_id");
         String docKey    = call.get("doc_key");
         String docType   = call.get("doc_type");
         String errorCode = call.get("error_code");
         String fileName  = call.get("file_name");
         Blob fileBody    = call.get("file_body");

         /*
          * Некорректные значения здесь являются технической ошибкой,
          * а не ошибкой внешнего protocol payload.
          */
         if( S.isNullOrEmpty(taskId) )
            throw Errors.xxiCallFailed( PREPARE_SEND_DOCUMENT, reqId, retCode, "out parameter 'task_id' is empty", null );

         if( S.isNullOrEmpty(docKey) )
             throw Errors.xxiCallFailed ( PREPARE_SEND_DOCUMENT, reqId,retCode,  "out parameter 'doc_key' is empty", null );

         if( S.isNullOrEmpty(fileName) )
             throw Errors.xxiCallFailed( PREPARE_SEND_DOCUMENT, reqId, retCode, "out parameter 'file_name' is empty", null );

         /* SEND_DOCUMENT должен содержать либо docType, либо errorCode. */
         boolean hasDocType   = !S.isNullOrEmpty(docType);
         boolean hasErrorCode = !S.isNullOrEmpty(errorCode);

         if( hasDocType == hasErrorCode )
            throw Errors.xxiCallFailed( PREPARE_SEND_DOCUMENT, reqId, retCode,
                 "SEND_DOCUMENT journal must contain exactly one of doc_type/error_code", null );

         if( fileBody == null )
            throw Errors.xxiCallFailed( PREPARE_SEND_DOCUMENT, reqId, retCode, "out parameter 'file_body' is null", null );

         final UUID taskUuid;
         final UUID docKeyUuid;

         try
         {
            taskUuid   = UUID.fromString(taskId);
            docKeyUuid = UUID.fromString(docKey);
         }
         catch(IllegalArgumentException e) {
            throw Errors.contract( "SEND_DOCUMENT journal contains invalid UUID", e, Map.of()  );
         }

         byte[] content;

         try
         {
            long length = fileBody.length();

            if( length <= 0)
                throw Errors.xxiCallFailed( PREPARE_SEND_DOCUMENT, reqId, retCode, "out parameter 'file_body' is empty", null );

            /*
             * SEND_DOCUMENT сейчас всё равно строится как byte[]
             * и затем Base64 string, поэтому payload > Integer.MAX_VALUE
             * принципиально не может быть обработан этой моделью.
             */
            int size = Math.toIntExact(length);
            content = fileBody.getBytes( 1L, size );
         }
         finally
         {
            try {
               fileBody.free();
            }
            catch(Exception ignored)
            { }
         }

         return new SendDocumentData(
            taskUuid,
            docKeyUuid,

            docType,
            errorCode,

            fileName,
            content
         );
      }
   }


   /** */
   private record SendDocumentData (

      UUID   taskId,
      UUID   docKey,

      String docType,
      String errorCode,

      String fileName,
      byte[] content
   )
   { }


   /** */
   private record SendDocumentPayload(

      String type,

      UUID taskId,
      UUID docKey,

      String docType,
      String errorCode,

      String docFileName,
      String docContent
   )
   { }
}