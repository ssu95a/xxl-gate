package ru.inversion.edo.xxl.xxi.command;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.xxi.protocol.XXLRequest;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.utils.Checks;
import ru.inversion.utils.S;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * <h5>Validator данных входного запроса XXLRequest.</h5>
 * <p>
 * Зона ответственности:
 * <ul>
 *    <li>Проверяет обязательные атрибуты XXLRequest;</li>
 *    <li>Проверяет version;</li>
 *    <li>Проверяет action;</li>
 *    <li>Проверяет mode;</li>
 *    <li>Проверяет формат timestamp;</li>
 *    <li>Диагностирует рассинхрон часов PostgreSQL и Spring-приложения;</li>
 *    <li>При ошибке бросает Errors.contract(...).</li>
 * </ul>
 * Не ходит в БД.
 * Не проверяет существование req_id.
 * Не проверяет статус mi_req.
 */
@Slf4j
@Component
public class XxiRequestValidator {

   private static final String VERSION_1_0 = "1.0";

   static final String ACTION_SEND = "send";

   /**
    * Timestamp формируется в PG MI_mbus.send_request:
    * <p>
    * to_char( clock_timestamp(), 'YYYY-MM-DD"T"HH24:MI:SS.MS TZH:TZM' )
    * <p>
    * Пример:
    * 2026-06-19T13:10:25.123 +03:00
    */
   private static final String PG_TIMESTAMP_PATTERN = "uuuu-MM-dd'T'HH:mm:ss.SSS xxx";

   private static final DateTimeFormatter PG_TIMESTAMP_FORMAT =
      new DateTimeFormatterBuilder().parseCaseSensitive().appendPattern(PG_TIMESTAMP_PATTERN).toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

   /** Если запрос из ПГ доходит c интервалом в TIMESTAMP_WARN_THRESHOLD и более то WARN. */
   private static final Duration TIMESTAMP_WARN_THRESHOLD = Duration.ofMinutes(5);

   /** Проверка данных Request из XXI */

   public void validate( XXLRequest request )
   {
      validateCommon(request);

      OffsetDateTime pgTimestamp = parseTimestamp(request);

      String action = normalize(request.getAction());

      if(ACTION_SEND.equals(action))
         validateSend(request);
      else
         validateDirect(request);

      request.parameters();

      logTimestampWarnThreshold( request, pgTimestamp, Instant.now() );
   }


   /** */
   public void validateCommon( XXLRequest request )
   {
      if( request == null )
          throw Errors.contract("XXLRequest is null");

      throwOnEmptyText( request.getVersion(), "version", request );

      if(!VERSION_1_0.equals( request.getVersion() ) )
          throw Errors.contract( "Unsupported XXLRequest version: " + request.getVersion(), params(request, "field", "version", "value", request.getVersion()) );

      throwOnEmptyText( request.getAction(),    "action",    request );
      throwOnNullValue( request.getCallUuid(),  "call_uuid", request );
      throwOnEmptyText( request.getTimestamp(), "timestamp", request );
   }


   /** */
   public void validateSend( XXLRequest request )
   {
      throwOnNullValue( request.getRequestId(),    "req_id",        request );
      throwOnNullValue( request.getExternalUuid(), "external_uuid", request );
      throwOnNullValue( request.getInfId(),        "inf_id",        request );
      throwOnNullValue( request.getCorrelationId(),"correlation_id",request );

      Checks.Numeric.positive( request.getInfId(), "inf_id" );
      Checks.Numeric.positive( request.getRequestId(), "req_id" );
   }


   /** */
   private void validateDirect(XXLRequest request)
   {
      Integer infId = request.getInfId();

      if( infId != null && infId <= 0 )
          throw Errors.contract( "Direct command inf_id must be positive or absent",
                                 params(request, "field", "inf_id", "value", infId) );

      Long requestId = request.getRequestId();
      if( requestId != null && requestId <= 0 )
          throw Errors.contract( "Direct command req_id must be positive or absent",
                 params( request, "field", "req_id", "value", requestId )
         );
   }

   /**
    * Разбирает timestamp, сформированный XXI.
    * <pg>
    * Package-private для unit-теста и повторного использования внутри пакета.
    */
   OffsetDateTime parseTimestamp( XXLRequest request )
   {
      try {
         return OffsetDateTime.parse( request.getTimestamp(), PG_TIMESTAMP_FORMAT );
      } catch (DateTimeParseException exception) {
         throw Errors.contract(
                 "Invalid XXLRequest timestamp: " + request.getTimestamp(),
                 exception,
                 params(
                         request,
                         "field", "timestamp",
                         "value", request.getTimestamp(),
                         "expected_format", PG_TIMESTAMP_PATTERN
                 )
         );
      }
   }

   /** */
   private void logTimestampWarnThreshold (
      XXLRequest request, OffsetDateTime pgTimestamp, Instant xxlTimestamp
   )
   {
      Duration skew = Duration.between( pgTimestamp.toInstant(), xxlTimestamp );

      if( skew.abs().compareTo(TIMESTAMP_WARN_THRESHOLD) <= 0 )
          return;

      log.warn( "XXI/XXL clock skew or bus delay: req_id={}, call_uuid={}, pg_timestamp={}, xxl_timestamp={}, skew_ms={}",
         request.getRequestId(), request.getCallUuid(), pgTimestamp, xxlTimestamp, skew.toMillis()
      );
   }

   /** */
   private void throwOnEmptyText(String value, String fieldName, XXLRequest request )
   {
      if( S.isNullOrEmpty(value) )
         throw Errors.contract( "Required XXLRequest attribute is missing: " + fieldName, params(request, "field", fieldName) );
   }

   /** */
   private void throwOnNullValue ( Object value, String fieldName, XXLRequest request )
   {
      if( value == null )
         throw Errors.contract (
            "Required XXLRequest attribute is missing: " + fieldName,
            params( request, "field", fieldName)
         );
   }

   /** */
   private String normalize(String value) {
      return value == null ? null : value.trim().toLowerCase();
   }

   /** */
   private Map<String, Object> params(
     XXLRequest request,
     Object... extra
   )
   {
      Map<String, Object> result = new LinkedHashMap<>();

      if( request != null )
          request.dump( result );

      if(extra != null)
      {
         int len =  extra.length % 2 != 0 ? extra.length -1 : extra.length;

         for( int i = 0; i < len; i += 2 )
         {
            Object key   = extra[i];
            Object value = extra[i + 1];

            if( key != null && value != null )
               result.put( String.valueOf(key), value );
         }
      }
      return result;
   }
}
