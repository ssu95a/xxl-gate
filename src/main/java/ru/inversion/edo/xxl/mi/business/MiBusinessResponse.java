package ru.inversion.edo.xxl.mi.business;

import ru.inversion.utils.IDumpable;
import ru.inversion.utils.S;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Результат обработки бизнес-запроса MI -> XXL -> XXI.
 */
public record MiBusinessResponse(

     UUID originalRequestId,

     String responseCode,
     String responseCategory,
     String responseInfo,

     Object data,

     Map<String, Object> attributes
)
   implements IDumpable
{
   public static final String CATEGORY_SUCCESS = "SUCCESS";
   public static final String CATEGORY_ERROR   = "ERROR";

   public static final String CODE_SUCCESS =
           "BUSINESS_REQUEST_COMPLETED";

   public MiBusinessResponse
   {
      if(S.isNullOrEmpty(responseCode) )
          throw new IllegalArgumentException( "responseCode must not be blank" );

      if( S.isNullOrEmpty(responseCategory) )
          throw new IllegalArgumentException( "responseCategory must not be blank" );

      responseCode     = responseCode.trim();
      responseCategory = CATEGORY_SUCCESS.equals(responseCategory) || CATEGORY_ERROR.equals(responseCategory) ? responseCategory : responseCategory.trim().toUpperCase();

      responseInfo     = S.isNullOrEmpty(responseInfo) ? null : responseInfo.trim();

      attributes       = immutableMap(attributes);
   }

   public boolean success()
   {
      return CATEGORY_SUCCESS.equals(responseCategory);
   }

   @Override
   public void dump( Map<String, Object> properties )
   {
      if( properties == null)
          return;

      properties.put( "original_request_id", originalRequestId );
      properties.put( "response_code",       responseCode );
      properties.put( "response_category",   responseCategory );
      properties.put( "response_info",       responseInfo );

      if(data instanceof Map<?, ?> map)
      {
         properties.put("data_class", data.getClass().getName());
         properties.put("data_keys",  map.keySet());
         properties.put("data_size",  map.size());
      }
      else
         if( data != null )
            properties.put( "data_class", data.getClass().getName() );

      properties.putAll(attributes);
   }

   /** */
   public static MiBusinessResponse success( UUID originalRequestId, Object data )
   {
      return success( originalRequestId, CODE_SUCCESS, "Business request completed", data, Map.of() );
   }

   /** */
   public static MiBusinessResponse success( UUID originalRequestId, String responseCode, String responseInfo )
   {
      return success( originalRequestId, responseCode, responseInfo, null, Map.of() );
   }

   /** */
   public static MiBusinessResponse success( UUID originalRequestId, String responseCode, String responseInfo, Object data, Map<String, Object> attributes )
   {
      return new MiBusinessResponse( originalRequestId, responseCode, CATEGORY_SUCCESS, responseInfo, data, attributes );
   }

   /** */
   public static MiBusinessResponse error( UUID originalRequestId, String responseCode, String responseInfo, Map<String, Object> attributes )
   {
      return new MiBusinessResponse( originalRequestId, responseCode, CATEGORY_ERROR, responseInfo, null, attributes );
   }

   private static Map<String, Object> immutableMap( Map<String, Object> source )
   {
      if(source == null || source.isEmpty())
         return Map.of();
      return Collections.unmodifiableMap( new LinkedHashMap<>(source) );
   }
}