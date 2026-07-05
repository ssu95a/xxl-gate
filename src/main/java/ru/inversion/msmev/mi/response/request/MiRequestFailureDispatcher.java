package ru.inversion.msmev.mi.response.request;

import org.springframework.stereotype.Component;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.mi.response.MiAsyncResponse;
import ru.inversion.msmev.mi.response.ProcessResult;
import ru.inversion.utils.Checks;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class MiRequestFailureDispatcher {

   private final Map<String, MiRequestFailureProcessor> handlers;

   private final DefaultMiRequestFailureHandler defaultHandler;

   /** */
   public MiRequestFailureDispatcher (List<MiRequestFailureProcessor> handlers, DefaultMiRequestFailureHandler defaultHandler )
   {
      this.handlers = buildSet( handlers == null ? Collections.emptyList() : handlers);
      this.defaultHandler = Checks.Require.object( defaultHandler, "defaultHandler" );
   }

   /** */
   public ProcessResult dispatch( MiAsyncResponse response )
   {
      String namespace = normalize( response.infNamespace() );

      MiRequestFailureProcessor processor = handlers.get(namespace);

      /* Частный обработчик для ВС есть. */
      if( processor != null )
      {
         ProcessResult result = processor.handle(response);
         return Objects.requireNonNull( result, processor.getClass().getName() + " returned null ProcessResult" );
      }

      /* Нет частного обработчика, зовем общего. */
      return defaultHandler.handle(response);
   }

   /** */
   private static Map<String, MiRequestFailureProcessor> buildSet ( List<MiRequestFailureProcessor> source )
   {
      Map<String, MiRequestFailureProcessor> result = new LinkedHashMap<>();

      for( MiRequestFailureProcessor processor : source )
      {
         for( String s : processor.supportsNamespaces() )
         {
            if( S.isNullOrEmpty(s) )
                continue;

            String namespace = normalize(s);

            MiRequestFailureProcessor previous = result.put(namespace, processor);

            if( previous != null )
               throw Errors.config (
                 "Duplicate request failure processor",
                 U.toMap( "inf_namespace", namespace, "processor_1", previous.getClass().getName(), "processor_2", processor.getClass().getName() )
               );
         }//end for

      }//end for

      return Collections.unmodifiableMap(result);
   }

   /** */
   private static String normalize(String value)
   {
      if( value == null )
          return null;

      String normalized = value.trim().toLowerCase(Locale.ROOT);

      return normalized.isEmpty() ? null : normalized;
   }
}