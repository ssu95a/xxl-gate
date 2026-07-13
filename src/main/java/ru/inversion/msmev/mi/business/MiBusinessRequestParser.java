package ru.inversion.msmev.mi.business;

import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.mi.transport.payload.ReceivedPayload;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.util.Attrs;

import java.util.Map;
import java.util.UUID;

@Component
public final class MiBusinessRequestParser
{
   public MiBusinessRequest parse(ReceivedMessage message)
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
         throw Errors.miBusinessPayloadBadFormat( "MI business requestType is empty", messageAttributes(message).toMap() );

      ReceivedPayload sourcePayload = message.getPayload();

      MiBusinessPayload payload =
           new MiBusinessPayload(
                sourcePayload,
                sourcePayload == null ? null : sourcePayload.contentType(),
                sourcePayload == null ? 0 : sourcePayload.size(),
                message.isFromS3()
           );

      return new MiBusinessRequest(
              message.getRequestId(),
              message.getMiCorrelationId(),
              requestType.trim(),
              message.getInfNamespace(),
              message.getCreatedAt(),
              message.getSourceSystem(),
              message.getSourceVersion(),
              payload,
              messageAttributes(message)
                      .put("request_type", requestType.trim())
                      .putIfNotNull("payload_content_type", payload.contentType())
                      .put("payload_size", payload.size())
                      .put("from_s3", payload.fromS3())
                      .toMap()
      );
   }

   /** */
   private static String readRequestType(ReceivedMessage message)
   {
      /*
       * Лучше routing брать из transport metadata,
       * а не из body большого файла.
       *
       * Приоритет можно уточнить:
       * 1. infNamespace
       * 2. fileName / message property
       * 3. отдельный transport header, если есть
       */
      if( message.getInfNamespace() != null && !message.getInfNamespace().isBlank() )
         return message.getInfNamespace();

      if( message.getFileName() != null && !message.getFileName().isBlank() )
         return message.getFileName();

      return null;
   }

   /** */
   private static Attrs messageAttributes(ReceivedMessage message)
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