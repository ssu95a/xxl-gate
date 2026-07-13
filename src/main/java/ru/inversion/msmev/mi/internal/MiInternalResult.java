package ru.inversion.msmev.mi.internal;

import ru.inversion.msmev.error.XXLException;

import java.util.Map;

public record MiInternalResult (

   String responseCode,
   String responseCategory,
   String responseInfo,

   Map<String, Object> data
)
{
   /** */
   public MiInternalResult
   {
      data = data == null ? Map.of() : Map.copyOf(data);
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