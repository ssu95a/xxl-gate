package ru.inversion.msmev.mi.internal;

import org.springframework.stereotype.Component;
import ru.inversion.msmev.error.Errors;
import ru.inversion.utils.U;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public final class MiInternalRequestDispatcher
{
   private final Map<String, MiInternalRequestHandler> handlers;

   public MiInternalRequestDispatcher(
           List<MiInternalRequestHandler> handlers
   )
   {
      this.handlers = buildIndex(handlers);
   }

   public MiInternalResult dispatch(
           MiInternalRequest request
   )
   {
      if( request == null )
      {
         throw Errors.miServiceBadFormat(
                 "MI internal request is null",
                 Map.of()
         );
      }

      String queryType =
              normalize(request.queryType());

      if( queryType.isEmpty() )
      {
         throw Errors.miServiceBadFormat(
                 "MI internal queryType is empty",
                 attributes(request)
         );
      }

      MiInternalRequestHandler handler =
              handlers.get(queryType);

      if( handler == null )
      {
         return MiInternalResult.error(
                 Errors.ResultCode.MI_SERVICE_UNSUPPORTED_REQUEST,
                 "Unsupported MI internal queryType: "
                         + request.queryType()
         );
      }

      MiInternalResult result =
              handler.handle(request);

      if( result == null )
      {
         throw Errors.miServiceFailed(
                 "MI internal handler returned null",
                 null,
                 U.toMap(
                         "query_type", queryType,
                         "handler", handler.getClass().getName()
                 )
         );
      }

      return result;
   }

   private Map<String, MiInternalRequestHandler> buildIndex(
           List<MiInternalRequestHandler> source
   )
   {
      Map<String, MiInternalRequestHandler> result =
              new LinkedHashMap<>();

      if( source == null )
         return Map.of();

      for( MiInternalRequestHandler handler : source )
      {
         if( handler == null )
         {
            throw new IllegalStateException(
                    "MI internal handler list contains null"
            );
         }

         if( handler.queryTypes() == null
                 || handler.queryTypes().isEmpty() )
         {
            throw new IllegalStateException(
                    "MI internal handler has no queryTypes: "
                            + handler.getClass().getName()
            );
         }

         for( String declaredQueryType :
                 handler.queryTypes() )
         {
            String queryType =
                    normalize(declaredQueryType);

            if( queryType.isEmpty() )
            {
               throw new IllegalStateException(
                       "MI internal handler declares empty queryType: "
                               + handler.getClass().getName()
               );
            }

            MiInternalRequestHandler previous =
                    result.putIfAbsent(queryType, handler);

            if( previous != null )
            {
               throw new IllegalStateException(
                       "Duplicate MI internal queryType '"
                               + queryType
                               + "': "
                               + previous.getClass().getName()
                               + " and "
                               + handler.getClass().getName()
               );
            }
         }
      }

      return Map.copyOf(result);
   }

   private String normalize(
           String value
   )
   {
      if( value == null )
         return "";

      return value
              .trim()
              .toUpperCase(Locale.ROOT);
   }

   private Map<String, Object> attributes(
           MiInternalRequest request
   )
   {
      Map<String, Object> result =
              new LinkedHashMap<>();

      result.put("message_id", request.messageId());
      result.put("query_type", request.queryType());
      result.put("created_at", request.createdAt());
      result.put("source_system", request.sourceSystem());
      result.put("source_version", request.sourceVersion());

      return result;
   }
}