package ru.inversion.msmev.mi.internal;

import ru.inversion.utils.IDumpable;

import java.util.Collections;
import java.util.Map;

public record InternalResult(

   String responseCode,
   String responseCategory,
   String responseInfo,
   String responseDetails,

   Map<String, Object> data
)
   implements IDumpable
{
   /** */
   public InternalResult
   {
      data = data == null || data.isEmpty() ? Map.of() : Collections.unmodifiableMap(data);
   }

   @Override
   public void dump( Map<String, Object> properties )
   {
      if( properties == null )
          return;

      properties.put("responseCategory", responseCategory);
      properties.put("responseCode",     responseCode    );
      properties.put("responseInfo",     responseInfo    );
      properties.put("responseDetails",  responseDetails );

      if( data != null && !data.isEmpty() )
           properties.putAll(data);
   }

   /** */
   public static InternalResult ok(Map<String, Object> data )
   {
      return new InternalResult (
        "SUCCESS",
        "SUCCESS",
        "OK",
        null,
        data
      );
   }

   /** */
   public static InternalResult ok( String info, Map<String, Object> data )
   {
      return new InternalResult( "SUCCESS", "SUCCESS", info, null, data );
   }

   /** */
   public static InternalResult ok(String code, String info, Map<String, Object> data )
   {
      return new InternalResult( code, "SUCCESS", info, null, data );
   }

   /** */
   public static InternalResult error(String code, String info, Map<String, Object> data )
   {
      return new InternalResult( code, "ERROR", info, null, data );
   }

   /** */
   public static InternalResult error( String code, String info )
   {
      return new InternalResult( code, "ERROR", info, null, Map.of() );
   }

   /** */
   public static InternalResult error( String code, String info, String details )
   {
      return new InternalResult( code, "ERROR", info, details, Map.of() );
   }
   public static InternalResult error( String code, String info, String details, Map<String, Object> data )
   {
      return new InternalResult( code, "ERROR", info, details, data );
   }

}