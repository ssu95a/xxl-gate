package ru.inversion.edo.xxl.mi.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Slf4j
public final class InternalRequestDispatcher
{
   private final Map<String, InternalRequestHandler> handlers;

   /** */
   public InternalRequestDispatcher(List<InternalRequestHandler> handlers )
   {
      this.handlers = buildRegistry( handlers );
   }

   /** */
   public InternalResult dispatch( InternalRequest request )
   {
      if( request == null )
          throw Errors.miInternalBadFormat( "MI internal request is null", Map.of() );

      if( request.messageId() == null )
          throw Errors.miInternalBadFormat( "MI internal messageId is null", request.dump() );

      String queryType = normalize( request.queryType() );

      if( queryType.isEmpty() )
          throw Errors.miInternalBadFormat( "MI internal queryType is empty", request.dump() );

      final InternalRequestHandler handler = handlers.get(queryType);

      if( handler == null )
          throw Errors.miInternalUnsupportedRequest( "Unsupported MI internal queryType: " + request.queryType(), request.dump() );

      InternalResult result = handler.handle( request );

      if( result == null )
          throw Errors.miInternalFailed( "MI internal handler returned null", null, U.toMap( "query_type", queryType, "handler", handler.getClass().getName() ) );

      return result;
   }

   /** */
   private static Map<String, InternalRequestHandler> buildRegistry( List<InternalRequestHandler> source )
   {
      final Map<String, InternalRequestHandler> result = new LinkedHashMap<>();

      if( source == null || source.isEmpty() )
          return Map.of();

      for( InternalRequestHandler handler : source )
      {
         if( handler == null )
             continue;

         if( handler.queryTypes() == null || handler.queryTypes().isEmpty() )
             continue;

         for( String declaredQueryType : handler.queryTypes() )
         {
            String queryType = normalize( declaredQueryType );

            if( queryType.isEmpty() ) {
               //throw new IllegalStateException("MI internal handler declares empty queryType: " + handler.getClass().getName());
               log.warn( "MI internal handler declares empty queryType: {}", handler.getClass().getName() );
               continue;
            }
            InternalRequestHandler previous = result.putIfAbsent( queryType, handler );

            if( previous != null )
                throw new IllegalStateException( "Duplicate MI internal queryType '" + queryType + "': handler_1 - '" + previous.getClass().getName() + "' and handler_2 - '" + handler.getClass().getName() + "'" );
         }
      }

      return Map.copyOf(result);
   }


   /** */
   private static String normalize( String value )
   {
      if( value == null )
         return S.EMPTY_STRING;

      return value.trim().toUpperCase(Locale.ROOT);
   }

}