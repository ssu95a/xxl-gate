package ru.inversion.msmev.mi.business;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.mi.transport.listener.MITransportListener;
import ru.inversion.msmev.util.XxlLog;

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
public class MiBusinessRequestListener {

   private final MiBusinessRequestDispatcher dispatcher;
   private final MiBusinessResponsePublisher publisher;

   @MITransportListener(queue = "${mi-edo.requests:mi-edo.requests}")
   public void handleRequest(ReceivedMessage message)
   {
      try( XxlLog.Scope ignored = XxlLog.module( XxlLog.Module.BUSINESS ) ) {
         MiBusinessResponse response = dispatcher.dispatch(message);
         publisher.publish(message, response);
      }
   }
}