package ru.inversion.msmev.mi.internal;

import ru.inversion.utils.IDumpable;

import java.util.Map;

public record InternalResult(

   String responseCode,
   String responseCategory,
   String responseInfo,

   Map<String, Object> data
)
   implements IDumpable
{
   /** */
   public InternalResult
   {
      data = data == null ? Map.of() : Map.copyOf(data);
   }

   @Override
   public void dump( Map<String, Object> properties )
   {
      if( properties == null )
          return;

      properties.put("responseCategory", responseCategory);
      properties.put("responseCode",     responseCode);
      properties.put("responseInfo",     responseInfo);

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
        data
      );
   }

   /** */
   public static InternalResult ok(String code, String info, Map<String, Object> data )
   {
      return new InternalResult( code, "SUCCESS", info, data );
   }

   /** */
   public static InternalResult error(String code, String info, Map<String, Object> data )
   {
      return new InternalResult( code, "ERROR", info, data );
   }

   /** */
   public static InternalResult error(String code, String info )
   {
      return new InternalResult( code, "ERROR", info, Map.of() );
   }

}