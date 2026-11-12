package ru.inversion.edo.xxl.xxi.command.mi_0600;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.transport.PayloadDto;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.utils.U;

import java.util.Base64;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MI_0606_Repository
{
   private static final String MEDIA_TYPE = MediaType.APPLICATION_JSON_VALUE;

   private final XxiRepositoryExecutor db;
   private final ObjectMapper objectMapper;

   /** */
   public PayloadDto prepareSendDocument(long reqId)
   {
      SendDocumentData data = loadSendDocument(reqId);

      try
      {
         byte[] payload = objectMapper.writeValueAsBytes (
            new SendDocumentPayload (
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
      catch(JsonProcessingException e) {
         throw Errors.payloadBuildFailed( "Не удалось сформировать SEND_DOCUMENT", e, U.toMap("req_id", reqId) );
      }
   }


   /** */
   private SendDocumentData loadSendDocument(long reqId)
   {
      return db.execute(
              "MI_0606.prepareSendDocument",
              U.toMap("req_id", reqId),
              tc -> {
                 // SQL появится после окончательного согласования,
                 // где храним taskId/docType/errorCode.
                 throw new UnsupportedOperationException();
              }
      );
   }


   private record SendDocumentData(
      UUID taskId,
      UUID docKey,
      String docType,
      String errorCode,
      String fileName,
      byte[] content
   )
   { }


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