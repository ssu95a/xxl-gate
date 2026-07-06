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

   public MiInternalRequestDispatcher( List<MiInternalRequestHandler> handlers )
   {
      this.handlers = buildIndex(handlers);
   }

   /**
    * Обрабатывает уже разобранный внутренний запрос MI.
    *
    * Transport parsing и публикация ответа сюда не входят.
    */
   public MiInternalResult dispatch( MiInternalRequest request )
   {
      if( request == null )
          throw Errors.miServiceBadFormat( "MI internal request is null", Map.of() );

      String operation = normalize( request.operation() );

      if( operation.isEmpty() )
          throw Errors.miServiceBadFormat( "MI internal operation is empty", attributes(request) );

      MiInternalRequestHandler handler = handlers.get(operation);

      if( handler == null )
          return new MiInternalResult( false, Errors.ResultCode.MI_SERVICE_UNSUPPORTED_REQUEST, "Unsupported MI internal operation: " + request.operation(), Map.of( "operation", request.operation() ) );

      MiInternalResult result = handler.handle(request);

      if( result == null )
          throw Errors.miServiceFailed( "MI internal handler returned null", null, U.toMap( "operation", operation, "handler", handler.getClass().getName()) );

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
            throw new IllegalStateException(
                    "MI internal handler list contains null"
            );

         if( handler.operations() == null
                 || handler.operations().isEmpty() )
         {
            throw new IllegalStateException(
                    "MI internal handler has no operations: "
                            + handler.getClass().getName()
            );
         }

         for( String declaredOperation :
                 handler.operations() )
         {
            String operation =
                    normalize(declaredOperation);

            if( operation.isEmpty() )
            {
               throw new IllegalStateException(
                       "MI internal handler declares empty operation: "
                               + handler.getClass().getName()
               );
            }

            MiInternalRequestHandler previous =
                    result.putIfAbsent(operation, handler);

            if( previous != null )
            {
               throw new IllegalStateException(
                       "Duplicate MI internal operation '"
                               + operation
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

   /** */
   private String normalize(String operation)
   {
      if( operation == null )
         return "";
      return operation.trim().toUpperCase(Locale.ROOT);
   }

   /** */
   private Map<String, Object> attributes( MiInternalRequest request )
   {
      Map<String, Object> result = new LinkedHashMap<>();

      result.put("message_id",   request.messageId());
      result.put("operation",    request.operation());
      result.put("inf_namespace",request.infNamespace() );
      result.put("created_at",   request.createdAt());

      return result;
   }
}