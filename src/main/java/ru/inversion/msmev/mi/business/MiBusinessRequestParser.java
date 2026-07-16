package ru.inversion.msmev.mi.business;

import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.util.Attrs;

import java.util.Map;

@Component
public final class MiBusinessRequestParser
{
   public MiBusinessRequest parse( ReceivedMessage message )
   {
      if( message == null )
      {
         throw Errors.miBusinessPayloadBadFormat(
           "MI business message is null",
           Map.of()
         );
      }

      String requestType = readRequestType(message);

      if( requestType == null || requestType.isBlank() )
      {
         throw Errors.miBusinessPayloadBadFormat(
            "MI business requestType is empty",
            messageAttrs(message).toMap()
         );
      }

      MiBusinessPayload payload = new MiBusinessPayload( message.getPayload() );

      MediaType mediaType = parseMediaType( message, payload );

      return new MiBusinessRequest(
              message.getRequestId(),
              message.getOriginalRequestId(),
              message.getMiCorrelationId(),
              requestType.trim(),
              message.getInfNamespace(),
              message.getCreatedAt(),
              message.getSourceSystem(),
              message.getSourceVersion(),
              payload,
              messageAttrs(message)
                      .put("request_type", requestType.trim()).put("payload_content_type", payload.contentType())
                      .put("payload_media_type", mediaType.toString())
                      .put("payload_size", payload.size())
                      .toMap()
      );
   }

   /** */
   private static String readRequestType( ReceivedMessage message )
   {
      if( message.getInfNamespace() != null && !message.getInfNamespace().isBlank() )
          return message.getInfNamespace();

      if( message.getFileName() != null && !message.getFileName().isBlank() )
          return message.getFileName();

      return null;
   }

   /** */
   private static MediaType parseMediaType( ReceivedMessage message, MiBusinessPayload payload )
   {
      String contentType = payload == null ? null : payload.contentType();

      if( contentType == null || contentType.isBlank() )
      {
         throw Errors.miBusinessPayloadBadFormat(
              "MI business payload contentType is empty",
              messageAttrs(message).toMap()
         );
      }

      try
      {
         return MediaType.parseMediaType(contentType);
      }
      catch( InvalidMediaTypeException exception )
      {
         throw Errors.miBusinessPayloadBadFormat(
                 "MI business payload contentType is invalid",
                 messageAttrs(message)
                         .put("payload_content_type", contentType)
                         .toMap()
         );
      }
   }

   /** */
   private static Attrs messageAttrs( ReceivedMessage message )
   {
      return Attrs.create()
           .putIfNotNull("request_id", message.getRequestId())
           .putIfNotNull("original_request_id", message.getOriginalRequestId())
           .putIfNotNull("mi_correlation_id", message.getMiCorrelationId())
           .putIfNotNull("inf_id", message.getInfId())
           .putIfNotNull("inf_namespace", message.getInfNamespace())
           .putIfNotNull("file_name", message.getFileName())
           .putIfNotNull("send_mode", message.getSendMode())
           .putIfNotNull("source_system", message.getSourceSystem())
           .putIfNotNull("source_version", message.getSourceVersion())
           .putIfNotNull("created_at", message.getCreatedAt())
           .putIfNotNull("occurred_at", message.getOccurredAt())
           .putIfNotNull("delivery_tag", message.getDeliveryTag())
           .put("from_s3", message.isFromS3());
   }
}