package ru.inversion.msmev.xxi.command;

import org.springframework.stereotype.Component;
import ru.inversion.msmev.dto.XXLRequest;
import ru.inversion.msmev.error.Errors;
import ru.inversion.utils.S;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <h5>Validator данных входного запроса XXLRequest.</h5>
 *
 * Зона ответственности:
 * <ul>
 *    <li>Проверяет обязательные атрибуты XXLRequest;</li>
 *    <li>Проверяет version;</li>
 *    <li>Проверяет action;</li>
 *    <li>Проверяет mode;</li>
 *    <li>При ошибке бросает Errors.contract(...).</li>
 *</ul>
 * Не ходит в БД.
 * Не проверяет существование req_id.
 * Не проверяет статус mi_req.
 */
@Component
public class XxiRequestValidator {

   private static final String VERSION_1_0 = "1.0";

   private static final String ACTION_SEND = "send";

   private static final String MODE_AUTO   = "auto";
   private static final String MODE_SYNC   = "sync";
   private static final String MODE_ASYNC  = "async";

   public void validate( XXLRequest request )
   {
      if( request == null )
          throw Errors.contract("XXLRequest is null");

      requireText( request.getVersion(), "version", request );

      if( !VERSION_1_0.equals(request.getVersion()) )
      {
         throw Errors.contract (
              "Unsupported XXLRequest version: " + request.getVersion(),
              params( request, "field", "version", "value", request.getVersion())
         );
      }

      requireText( request.getAction(), "action", request );

      if (!ACTION_SEND.equals(normalize(request.getAction()))) {
         throw Errors.contract(
              "Unsupported XXLRequest action: " + request.getAction(),
              params( request, "field", "action", "value", request.getAction())
         );
      }

      requireText( request.getMode(), "mode", request );

      String mode = normalize(request.getMode());

      if(!MODE_AUTO.equals(mode) && !MODE_SYNC.equals(mode) && !MODE_ASYNC.equals(mode)) {
         throw Errors.contract(
              "Unsupported XXLRequest mode: " + request.getMode(),
              params(request, "field", "mode", "value", request.getMode())
         );
      }

      requireNotNull( request.getRequestId(),    "req_id",        request );
      requireNotNull( request.getExternalUuid(), "external_uuid", request );
      requireNotNull( request.getInfId(),        "inf_id",        request );
      requireNotNull( request.getCorrelationId(),"correlation_id",request );
      requireNotNull( request.getCallUuid(),     "call_uuid",     request );
   }

   /** */
   private void requireText( String value, String fieldName, XXLRequest request) {
      if( S.isNullOrEmpty(value) )
          throw Errors.contract( "Required XXLRequest attribute is missing: " + fieldName, params(request, "field", fieldName) );
   }

   /** */
   private void requireNotNull( Object value, String fieldName, XXLRequest request) {
      if( value == null )
          throw Errors.contract( "Required XXLRequest attribute is missing: " + fieldName, params( request, "field", fieldName ) );
   }

   /** */
   private String normalize( String value ) {
      return value == null ? null : value.trim().toLowerCase();
   }

   /** */
   private Map<String, Object> params( XXLRequest request, Object... extra) {

      Map<String, Object> result = new LinkedHashMap<>();

      if( request != null )
          request.dump(result);

      if( extra != null )
      {
         if( extra.length % 2 != 0 )
             throw new IllegalArgumentException("params requires even number of arguments");

         for( int i = 0; i < extra.length; i += 2 )
         {
            Object key   = extra[i];
            Object value = extra[i + 1];

            if (key != null && value != null) {
               result.put(String.valueOf(key), value);
            }
         }
      }

      return result;
   }
}