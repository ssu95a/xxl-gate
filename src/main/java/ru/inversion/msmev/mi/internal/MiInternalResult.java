package ru.inversion.msmev.mi.internal;

import ru.inversion.msmev.error.XXLException;
import ru.inversion.utils.IDumpable;

import java.util.Map;

public record MiInternalResult (

   String responseCode,
   String responseCategory,
   String responseInfo,

   Map<String, Object> data
)
   implements IDumpable
{
   /** */
   public MiInternalResult
   {
      data = data == null ? Map.of() : Map.copyOf(data);
   }

   @Override
   public void dump( Map<String, Object> properties )
   {
      if( properties == null )
          return;

      properties.put("response_code", responseCode);
      properties.put("response_category", responseCategory);
      properties.put("response_info", responseInfo);

      if( data != null && !data.isEmpty() )
           properties.putAll(data);
   }

   /** */
   public static MiInternalResult ok( Map<String, Object> data )
   {
      return new MiInternalResult( "0", "OK", "OK", data );
   }

   /** */
   public static MiInternalResult error( String code, String info, Map<String, Object> data )
   {
      return new MiInternalResult( code, "ERROR", info, data );
   }

   /** */
   public static MiInternalResult error( String code, String info )
   {
      return new MiInternalResult( code, "ERROR", info, Map.of() );
   }

   /** */
   public static MiInternalResult error( XXLException e )
   {
      return new MiInternalResult( e.getResultCode(), "ERROR", e.getMessage(), e.getAttributes() );
   }
}