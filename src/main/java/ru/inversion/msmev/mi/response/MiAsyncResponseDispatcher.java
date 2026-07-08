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
 * <p>Зона ответственности:</p>
 * <ul>
 *    <li>Распарсить ReceivedMessage в MiAsyncResponse;</li>
 *    <li>Выбрать подходящий MiAsyncResponseHandler;</li>
 *    <li>Вызвать handler.handle(response);</li>
 *    <li>Преобразовать исключение в ProcessResult.</li>
 * </ul>
 * <p>
 * <p>Не выполняет:</p>
 * <ul>
 *    <li>ACK напрямую;</li>
 *    <li>Чтение из очереди;</li>
 *    <li>Проверку доступности XXI;</li>
 *    <li>Анализ SQLException и SQLState;</li>
 *    <li>Publish ответов в MI.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MiAsyncResponseDispatcher {

   private final MiAsyncResponseParser parser;
   private final List<MiAsyncResponseHandler> handlers;

   public ProcessResult dispatch(ReceivedMessage message)
   {
      try {

         MiAsyncResponse response = parser.parse(message);
         MiAsyncResponseHandler handler = findHandler(response);

         return handler.handle(response);

      } catch (Exception exception) {
         return toProcessResult(exception);
      }
   }

   private MiAsyncResponseHandler findHandler( MiAsyncResponse response )
   {
      for( MiAsyncResponseHandler handler : handlers )
      {
         if( handler.supports(response) )
             return handler;
      }

      throw Errors.miResponseBadFormat( "MiAsyncResponseHandler not found", response.parameters() );
   }

   /** */
   private ProcessResult toProcessResult( Exception failure )
   {
      XXLException exception = normalize(failure);

      logException(exception);

      Map<String, Object> parameters = new LinkedHashMap<>();

      parameters.put( "namespace", exception.getNamespace().name() );

      if( exception.getAttributes() != null )
          parameters.putAll( exception.getAttributes() );

      if( shouldRetry(exception) )
          return ProcessResult.retryable( exception.getResultCode(), exception.getMessage(), parameters );

      return ProcessResult.terminal( exception.getResultCode(), exception.getMessage(), parameters );
   }


   /** */
   private XXLException normalize ( Throwable throwable )
   {
      if( throwable instanceof XXLException exception )
         return exception;

      return Errors.internal (
        "Unexpected async response processing error",
        throwable, U.toMap( "exception", throwable == null ? null : throwable.getClass().getName())
      );
   }


   /**
    * Повторить ли сообщение!
    * <p>
    * Работает только для временных ошибки инфраструктуры БД.
    */
   private boolean shouldRetry( XXLException exception )
   {
      return switch( exception.getResultCode() ) {
         case Errors.ResultCode.TECHNICAL_BREAK,
              Errors.ResultCode.DB_ERROR -> true;
         default -> false;
      };
   }


   /** */
   private void logException( XXLException exception )
   {
      if( exception.getLogPolicy() == Errors.LogPolicy.WARN_NO_STACK )
      {
         log.warn( "MI async response failure: " + "namespace={}, resultCode={}, " + "message={}, params={}",
                   exception.getNamespace(), exception.getResultCode(), exception.getMessage(), exception.getAttributes() );
         return;
      }

      log.error( "MI async response failure: namespace={}, resultCode={}, message={}, params={}",
                 exception.getNamespace(), exception.getResultCode(), exception.getMessage(), exception.getAttributes(), exception
      );
   }
}