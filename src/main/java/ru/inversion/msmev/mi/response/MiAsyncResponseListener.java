package ru.inversion.msmev.mi.response;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.exception.MiTransportRetryException;
import ru.inversion.mi.transport.exception.MiTransportTerminalException;
import ru.inversion.mi.transport.listener.MITransportListener;
import ru.inversion.mi.transport.ReceivedMessage;

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
    *
    * Terminal ошибки тоже отдаём transport-у.
    * Listener не принимает ACK/DLQ/log-and-drop решений сам:
    * это часть delivery policy mi-transport.
    */
   @MITransportListener(queue = "${mi-edo.responses:mi-edo.responses}")
   public void handleResponse( ReceivedMessage message )
   {
      ProcessResult result = dispatcher.dispatch( message );

      if( result.success() )
          return;

      if( result.shouldRetry() )
          throw new MiTransportRetryException( result.resultCode(), result.resultInfo() );

      throw new MiTransportTerminalException( result.resultCode(), result.resultInfo() );
   }
}