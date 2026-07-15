package ru.inversion.msmev.mi.business;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.mi.transport.listener.MITransportListener;
import ru.inversion.msmev.mi.response.MiAsyncResponse;
import ru.inversion.msmev.util.XxlLog;

import java.util.concurrent.TimeUnit;

/**
 * <h6>Listener бизнес-запросов S -> XXI (MI -> XXL).</h6>
 * <p>
 * Queue:
 * - input: mi-edo.requests
 * - output: xxl.responses
 *
 * Зона ответственности:
 * - получает бизнес-запрос от MI;
 * - вызывает MiBusinessRequestDispatcher;
 * - публикует результат обработки в xxl.responses;
 *
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MiBusinessRequestListener {

   private final MiBusinessRequestDispatcher dispatcher;
   private final MiBusinessResponsePublisher publisher;

   @MITransportListener(queue = "${mi-edo.requests:mi-edo.requests}")
   public void handleRequest( ReceivedMessage message )
   {
      try( XxlLog.Scope ignored = XxlLog.module( XxlLog.Module.BUSINESS ) )
      {
         long startedAt = System.nanoTime();

         log.info( "MI business request received: {}", MiAsyncResponse.messageParameters(message) );

         MiBusinessResponse response = dispatcher.dispatch(message);

         publisher.publish(message, response);

         long elapsedMs = TimeUnit.NANOSECONDS.toMillis( System.nanoTime() - startedAt );

         log.info( "MI business request processed: responseCode={}, responseInfo={}, elapsedMs={}", response.responseCode(), response.responseInfo(), elapsedMs );
      }
   }
}