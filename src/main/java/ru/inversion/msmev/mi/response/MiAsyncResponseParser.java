package ru.inversion.msmev.mi.response;

import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.transport.XxlMiEnvelope;
import ru.inversion.utils.S;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Parser transport envelope для сообщений из mi-edo.responses.
 *
 * Зона ответственности:
 * - проверить ReceivedMessage;
 * - прочитать fileData в rawPayload;
 * - определить первичный kind;
 * - создать MiAsyncResponse с исходным sourceMessage.
 */
@Component
public class MiAsyncResponseParser {

   /** */
   public MiAsyncResponse parse( ReceivedMessage m )
   {
      validateContainer(m);

      String rawPayload = new String( m.getFileData(), StandardCharsets.UTF_8 );

      return new MiAsyncResponse (
        m,
        detectKind(rawPayload),

        null,
        null,
        null,
        null,
        null,

        null,
        null,
        null,

        rawPayload,
        OffsetDateTime.now(),

        baseAttributes(m)
      );
   }

   private void validateContainer( ReceivedMessage message ) {

      if( message == null )
          throw Errors.miResponseBadFormat( "ReceivedMessage is null", Collections.emptyMap() );

      if( S.isNullOrEmpty(message.getRequestId() ) )
          throw Errors.miResponseBadFormat( "ReceivedMessage.requestId is empty", baseAttributes(message) );

      if( S.isNullOrEmpty(message.getOriginalRequestId()) )
          throw Errors.miResponseBadFormat( "ReceivedMessage.originalRequestId is empty", baseAttributes(message) );

      if( S.isNullOrEmpty(message.getMiCorrelationId()) )
         throw Errors.miResponseBadFormat( "ReceivedMessage.miCorrelationId is empty", baseAttributes(message) );

      if( message.getFileData() == null || message.getFileData().length == 0 )
         throw Errors.miResponseBadFormat( "ReceivedMessage.fileData is empty", baseAttributes(message) );
   }

   /** */
   private MiAsyncResponseKind detectKind( String rawPayload )
   {
      String text = rawPayload == null ? "" : rawPayload.toLowerCase();

      if (text.contains("container_rejected")
              || text.contains("containerrejected")
              || text.contains("dtcm_rejected")
              || text.contains("validation_error")
              || text.contains("xsd")) {
         return MiAsyncResponseKind.REQUEST_FAILED;
      }

      return MiAsyncResponseKind.ITEM_RESULT;
   }

   private Map<String, Object> baseAttributes(ReceivedMessage message) {

      Map<String, Object> map = new LinkedHashMap<>();

      if( message == null )
         return map;

      map.put("request_id", message.getRequestId());
      map.put("original_request_id", message.getOriginalRequestId());
      map.put("mi_correlation_id", message.getMiCorrelationId());
      map.put("file_name", message.getFileName());
      map.put("file_size", message.getFileSize());
      map.put("from_s3", message.isFromS3());
      map.put("delivery_tag", message.getDeliveryTag());
      map.put("send_mode", message.getSendMode());

      return map;
   }

   private boolean isBlank(String value) {
      return value == null || value.isBlank();
   }
}