package ru.inversion.msmev.mi.response;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.error.XXLException;
import ru.inversion.msmev.mi.IMIEnvelope;
import ru.inversion.utils.U;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <h5>Dispatcher async-ответов MI -> XXL -> XXI</h5>
 * <p>
 * Зона ответственности:
 * <ul>
 * <li>Распарсить ReceivedMessage, полученное в MiAsyncResponseListener, в MiAsyncResponse;</li>
 * <li>Выбрать подходящий MiAsyncResponseHandler;
 * <li>Вызвать handler.handle(response);
 * <li>Преобразовать Throwable в ProcessResult;
 * </ul>
 * Не делает:
 * <ul>
 * <li>ACK напрямую;
 * <li>Чтение из очереди;
 * <li>Publish ответов в MI;
 * <li>Обработку MI -> XXI (СМЭВ запросы в ЦАБС, когда мы ответчики) business request из mi-edo.requests;
 * <li>Обработку MI internal request из mi-int.request. (Запросы от самой MI в ЦАБС)
 * </ul>
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class MiAsyncResponseDispatcher {

   private final MiAsyncResponseParser parser;
   private final List<MiAsyncResponseHandler> handlers;

   public ProcessResult dispatch( ReceivedMessage message )
   {
      try {

         IMIEnvelope envelope = parser.parse(message);
         MiAsyncResponseHandler handler = findHandler(envelope);

         return handler.handle(envelope);

      } catch (Throwable e) {
         return toProcessResult(e);
      }
   }

   /** */
   private MiAsyncResponseHandler findHandler(  IMIEnvelope envelope )
   {

      for( MiAsyncResponseHandler handler : handlers )
      {
         if( handler.supports(envelope) )
            return handler;
      }

      throw Errors.miResponseBadFormat( "MiAsyncResponseHandler not found", Map.of() );
      /*
              U.toMap(
                      "kind", response.kind(),
                      "req_id", response.reqId(),
                      "external_uuid", response.requestExternalUuid(),
                      "item_external_uuid",
                      response.itemExternalUuid(),
                      "inf_id",
                      response.infId(),
                      "request_id",
                      response.requestId(),
                      "original_request_id",
                      response.originalRequestId(),
                      "mi_correlation_id",
                      response.miCorrelationId()
              )
      );

       */
   }

   private ProcessResult toProcessResult( Throwable throwable ) {
      XXLException exception = normalize(throwable);

      logException(exception);

      Map<String, Object> parameters = new LinkedHashMap<>();
      parameters.put("namespace", exception.getNamespace().name());
      parameters.putAll(exception.getParameters());

      if (shouldRetry(exception)) {
         return ProcessResult.retryable(
                 exception.getResultCode(),
                 exception.getMessage(),
                 parameters
         );
      }

      return ProcessResult.terminal(
              exception.getResultCode(),
              exception.getMessage(),
              parameters
      );
   }

   /** */
   private XXLException normalize( Throwable throwable ) {

      if( throwable instanceof XXLException exception )
          return exception;

      return Errors.internal(
         "Unexpected async response processing error",
         throwable,
         U.toMap( "exception", throwable == null ? null : throwable.getClass().getName() )
      );
   }

   /** */
   private boolean shouldRetry( XXLException exception ) {
        /*
          Retry — это локальная политика очереди mi-edo.responses.

          Сейчас:
          - DB_ERROR можно повторить;
          - bad format / not found / duplicate conflict / config / payload обычно terminal.

          Если транспорт не поддерживает нормальный retry/DLQ,
          здесь лучше временно всегда возвращать false.
        */
      return Errors.ResultCode.DB_ERROR.equals(exception.getResultCode());
   }

   private void logException(XXLException exception) {
      if (exception.getLogPolicy() == Errors.LogPolicy.WARN_NO_STACK) {
         log.warn(
                 "MI async response failure: namespace={}, resultCode={}, message={}, params={}",
                 exception.getNamespace(),
                 exception.getResultCode(),
                 exception.getMessage(),
                 exception.getParameters()
         );
         return;
      }

      log.error(
              "MI async response failure: namespace={}, resultCode={}, message={}, params={}",
              exception.getNamespace(),
              exception.getResultCode(),
              exception.getMessage(),
              exception.getParameters(),
              exception
      );
   }
}