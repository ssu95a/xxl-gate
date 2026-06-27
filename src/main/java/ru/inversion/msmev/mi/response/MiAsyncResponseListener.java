package ru.inversion.msmev.mi.response;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
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
 * - по ProcessResult принять решение:
 *     success       -> ACK;
 *     terminal      -> ACK, ошибка уже зафиксирована;
 *     shouldRetry   -> exception для retry/nack, если транспорт это поддерживает.
 *
 * Не делает:
 * - парсинг payload;
 * - вызовы XXI;
 * - to_Error;
 * - запись item-result.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MiAsyncResponseListener {

   private final MiAsyncResponseDispatcher dispatcher;

   /** */
   @MITransportListener(queue = "${mi-edo.responses:mi-edo.responses}")
   public void handleResponse(ReceivedMessage message)
   {
      ProcessResult result = dispatcher.dispatch(message);

      if( result.success() )
          return;

      if( result.shouldRetry() )
          throw new MiAsyncResponseRetryException(result);

      throw new MiAsyncResponseTerminalException(result);
   }
}