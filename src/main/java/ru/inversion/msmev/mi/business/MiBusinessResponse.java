package ru.inversion.msmev.mi.business;

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
{
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