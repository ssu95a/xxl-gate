package ru.inversion.msmev.mi.internal;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Один внутренний запрос MI к XXL.
 * <p>
 * Payload не читается при создании request.
 */
public record MiInternalRequest(
        String messageId,
        String queryType,
        Map<String, Object> params,
        OffsetDateTime createdAt,
        String sourceSystem,
        String sourceVersion
)
{
   public MiInternalRequest
   {
      params = params == null ? Map.of() : Map.copyOf(params);
   }
}