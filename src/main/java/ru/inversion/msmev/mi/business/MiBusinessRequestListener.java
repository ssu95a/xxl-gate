package ru.inversion.msmev.mi.business;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.mi.transport.listener.MITransportListener;

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
 * - если не удалось опубликовать ответ, бросает exception для retry/nack.
 *
 * Не обрабатывает async-ответы на XXI -> S.
 */
@Component
@RequiredArgsConstructor
public class MiBusinessRequestListener {

   private final MiBusinessRequestDispatcher dispatcher;
   private final MiBusinessResponsePublisher publisher;

   @MITransportListener(queue = "mi-edo.requests")
   public void handleRequest(ReceivedMessage message) {
      MiBusinessResponse response = dispatcher.dispatch(message);
      publisher.publish(response);
   }
}