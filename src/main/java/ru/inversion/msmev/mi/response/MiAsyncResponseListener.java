package ru.inversion.msmev.mi.response;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.exception.MiTransportRetryException;
import ru.inversion.mi.transport.exception.MiTransportTerminalException;
import ru.inversion.mi.transport.listener.MITransportListener;
import ru.inversion.mi.transport.ReceivedMessage;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static ru.inversion.msmev.mi.response.MiAsyncResponse.messageParameters;

/**
 * <h5>Listener очереди async-ответов от MI на запросы от XXL.</h5>
 * <p>
 * Queue:
 * - mi-edo.responses
 * <p>
 * Зона ответственности:
 * - принять ReceivedMessage из очереди;
 * - передать сообщение в MiAsyncResponseDispatcher;
 * - преобразовать ProcessResult в transport outcome:
 *     success     -> normal return;
 *     retryable   -> MiTransportRetryException;
 *     terminal    -> MiTransportTerminalException.
 * <p>
 * ACK/DLQ/log-and-drop решения принадлежат mi-transport.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MiAsyncResponseListener {

   private final MiAsyncResponseDispatcher dispatcher;

   /*
    * Retryable ошибки возвращаем transport-у как retry.
    */
   @MITransportListener(queue = "${mi-edo.responses:mi-edo.responses}")
   public void handleResponse( ReceivedMessage message )
   {
      long startedAt = System.nanoTime();

      Map<String, Object> messageInfo = messageParameters( message );

      log.info( "MI async response received: {}", messageInfo );

      ProcessResult result = dispatcher.dispatch( message );

      long elapsedMs = TimeUnit.NANOSECONDS.toMillis( System.nanoTime() - startedAt );

      if( result.success() ) {
         log.info( "MI async response processed: resultCode={}, resultInfo={}, elapsedMs={}, params={}", result.resultCode(), result.resultInfo(), elapsedMs, result.parameters() );
         return;
      }

      if( result.shouldRetry() ) {
         log.warn( "MI async response retry: resultCode={}, resultInfo={}, elapsedMs={}, params={}", result.resultCode(), result.resultInfo(), elapsedMs, result.parameters() );
         throw new MiTransportRetryException(result.resultCode(), result.resultInfo());
      }

      log.error( "MI async response terminal: resultCode={}, resultInfo={}, elapsedMs={}, params={}", result.resultCode(), result.resultInfo(), elapsedMs, result.parameters() );

      throw new MiTransportTerminalException( result.resultCode(), result.resultInfo() );
   }
}