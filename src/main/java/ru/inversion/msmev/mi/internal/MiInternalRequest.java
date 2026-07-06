package ru.inversion.msmev.mi.internal;

import ru.inversion.mi.transport.payload.ReceivedPayload;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Один внутренний запрос MI к XXL.
 * <p>
 * Payload не читается при создании request.
 */
public record MiInternalRequest(
   UUID messageId,
   String operation,
   String infNamespace,
   OffsetDateTime createdAt,
   ReceivedPayload payload,
   Map<String, Object> headers
)
{
   public MiInternalRequest
   {
      headers = headers == null || headers.isEmpty() ? Map.of() : Collections.unmodifiableMap( new LinkedHashMap<>(headers) );
   }
}