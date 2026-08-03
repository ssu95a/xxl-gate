package ru.inversion.edo.xxl.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** JSON */
public final class JsonMaps
{
   private static final ObjectMapper MAPPER = new ObjectMapper();

   private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =new TypeReference<>(){ };

   private JsonMaps()
   {
   }

   /** */
   public static Map<String, Object> jsonToMap(String json)
   {
      if( json == null || json.isBlank() )
          return Map.of();

      try
      {
         LinkedHashMap<String, Object> result = MAPPER.readValue(json, MAP_TYPE);

         if( result == null )
            throw new IllegalArgumentException( "JSON value must be an object" );

         return Collections.unmodifiableMap(result);
      }
      catch(JsonProcessingException e) {
         throw new IllegalArgumentException( "JSON value must be an object", e );
      }
   }}