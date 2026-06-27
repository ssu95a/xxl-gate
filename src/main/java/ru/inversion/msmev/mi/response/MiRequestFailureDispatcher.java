package ru.inversion.msmev.mi.response;

import org.springframework.stereotype.Component;
import ru.inversion.msmev.error.Errors;
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

   private final Map<String, MiRequestFailureProcessor> processors;

   private final DefaultMiRequestFailureHandler defaultHandler;

   public MiRequestFailureDispatcher(
           List<MiRequestFailureProcessor> processors,
           DefaultMiRequestFailureHandler defaultHandler
   ) {
      this.processors =
              buildIndex(
                      processors == null
                              ? Collections.emptyList()
                              : processors
              );

      this.defaultHandler =
              Objects.requireNonNull(
                      defaultHandler,
                      "defaultHandler"
              );
   }

   public ProcessResult dispatch(
           MiAsyncResponse response
   ) {
      String namespace =
              normalize(response.infNamespace());

      MiRequestFailureProcessor processor =
              processors.get(namespace);

      /*
       * Частный обработчик найден.
       */
      if (processor != null) {
         ProcessResult result =
                 processor.handle(response);

         return Objects.requireNonNull(
                 result,
                 processor.getClass().getName()
                         + " returned null ProcessResult"
         );
      }

      /*
       * Для интеграции нет частного обработчика.
       */
      return defaultHandler.handle(response);
   }

   private Map<String, MiRequestFailureProcessor> buildIndex( List<MiRequestFailureProcessor> source )
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

            if(previous != null )
               throw Errors.config (
                 "Duplicate request failure processor",
                 U.toMap( "inf_namespace", namespace, "processor_1", previous.getClass().getName(), "processor_2", processor.getClass().getName() )
               );
         }//end for

      }//end for

      return Collections.unmodifiableMap(result);
   }

   /** */
   private String normalize(String value)
   {
      if( value == null )
          return null;

      String normalized = value.trim().toLowerCase(Locale.ROOT);

      return normalized.isEmpty() ? null : normalized;
   }
}