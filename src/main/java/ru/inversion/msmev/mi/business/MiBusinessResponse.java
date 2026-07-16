package ru.inversion.msmev.mi.business;

import ru.inversion.utils.IDumpable;

import java.util.Map;
import java.util.UUID;

public record MiBusinessResponse (
   UUID originalRequestId,
   String responseCode,
   String responseCategory,
   String responseInfo,
   Object data,
   Map<String, Object> attributes
)
   implements IDumpable
{
   @Override
   public void dump( Map<String, Object> properties )
   {
      if( properties == null )
          return;

      properties.put( "original_request_id", originalRequestId);
      properties.put( "response_code",       responseCode     );
      properties.put( "response_category",   responseCategory );
      properties.put( "response_info",       responseInfo     );

      if( data instanceof Map<?, ?> map )
          properties.put("data", map);
      else if( data != null )
         properties.put("data_class", data.getClass().getName());

      properties.putAll( attributes );
   }

   /** */
   public static MiBusinessResponse ok(UUID originalRequestId, Object data)
   {
      return new MiBusinessResponse( originalRequestId, "0", "OK", "OK", data, Map.of() );
   }

   /** */
   public static MiBusinessResponse error( UUID originalRequestId, String code, String info, Map<String, Object> attributes )
   {
      return new MiBusinessResponse( originalRequestId, code, "ERROR", info, Map.of(), attributes == null ? Map.of() : Map.copyOf(attributes));
   }
}