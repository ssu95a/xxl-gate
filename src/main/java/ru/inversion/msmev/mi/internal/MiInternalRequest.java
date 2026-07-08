package ru.inversion.msmev.mi.internal;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record MiInternalRequest (
   UUID messageId,
   String queryType,
   Map<String, Object> params,
   OffsetDateTime createdAt,
   String sourceSystem,
   String sourceVersion
)
{
   public MiInternalRequest
   {
      params = params == null || params.isEmpty() ? Map.of() : Collections.unmodifiableMap( new LinkedHashMap<>(params) );
   }
}