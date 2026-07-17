package ru.inversion.msmev.mi.business;


import ru.inversion.utils.IDumpable;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record MiBusinessRequest(
   UUID messageId,
   UUID originalRequestId,
   UUID correlationId,
   String requestType,
   String infNamespace,
   OffsetDateTime createdAt,
   String sourceSystem,
   String sourceVersion,
   MiBusinessPayload  payload,
   Map<String, Object> attributes
)
   implements IDumpable
{
   public MiBusinessRequest
   {
      attributes = attributes == null || attributes.isEmpty() ? Map.of() : Map.copyOf(attributes);
   }

   @Override
   public void dump( Map<String, Object> properties )
   {
      if( properties == null )
          return;

      properties.putAll(attributes);

      properties.put("message_id",          messageId);
      properties.put("original_request_id", originalRequestId);
      properties.put("mi_correlation_id",   correlationId);
      properties.put("request_type",        requestType);
      properties.put("inf_namespace",       infNamespace);
      properties.put("created_at",          createdAt);
      properties.put("source_system",       sourceSystem);
      properties.put("source_version",      sourceVersion);

      if( payload != null )
      {
         properties.put("payload_content_type", payload.contentType());
         properties.put("payload_size", payload.size());
      }
   }
}