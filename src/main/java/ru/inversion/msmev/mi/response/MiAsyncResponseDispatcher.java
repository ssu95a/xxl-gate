package ru.inversion.msmev.mi.response;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.error.XXLException;
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

         MiAsyncResponse response = parser.parse(message);
         MiAsyncResponseHandler handler = findHandler(response);

         return handler.handle(response);

      } catch( Exception e ) {
         return toProcessResult(e);
      }
   }

   /** */
   private MiAsyncResponseHandler findHandler( MiAsyncResponse envelope )
   {

      for( MiAsyncResponseHandler handler : handlers )
      {
         if( handler.supports(envelope) )
             return handler;
      }

      throw Errors.miResponseBadFormat( "MiAsyncResponseHandler not found", envelope.parameters() );
   }

   /** */
   private ProcessResult toProcessResult( Exception e )
   {

      XXLException exception = normalize(e);

      logException(exception);

      Map<String, Object> parameters = new LinkedHashMap<>();
      parameters.put   ( "namespace", exception.getNamespace().name() );
      parameters.putAll( exception.getParameters() );

      if( shouldRetry(exception) )
         return ProcessResult.retryable( exception.getResultCode(), exception.getMessage(), parameters );

      return ProcessResult.terminal( exception.getResultCode(), exception.getMessage(), parameters );
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

   /** Если из нашей базы прилетело (обслуживание ЦАБС), то ставим на повтор */
   private boolean shouldRetry( XXLException exception ) {
      return Errors.ResultCode.DB_ERROR.equals( exception.getResultCode());
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