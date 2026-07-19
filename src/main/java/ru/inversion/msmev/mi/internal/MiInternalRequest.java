package ru.inversion.msmev.mi.internal;

import ru.inversion.utils.IDumpable;

import java.time.OffsetDateTime;
import java.util.Collections;
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
        implements IDumpable

{
   public MiInternalRequest
   {
      params = params == null || params.isEmpty() ? Map.of() : Collections.unmodifiableMap(params);
   }

   @Override
   public void dump( Map<String, Object> properties ) {

      if( properties == null )
         return;

      properties.put("created_at",     createdAt );
      properties.put("query_type",     queryType );
      properties.put("source_system",  sourceSystem);
      properties.put("source_version", sourceVersion);

      if( params != null && !params.isEmpty() )
      {
         properties.put("params_keys", params.keySet() );
         properties.put("params_size", params.size()   );
      }

   }
}