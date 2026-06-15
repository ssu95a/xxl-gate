package ru.inversion.msmev.mi.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.mi.transport.listener.MITransportListener;

/**
 * Listener технических sync-запросов MI -> XXL.
 *
 * Queue:
 * - mi-int.request
 *
 * Зона ответственности:
 * - принимает технический request от MI;
 * - вызывает MiInternalRequestDispatcher;
 * - возвращает MiInternalResponse в рамках sync transport-семантики.
 *
 * Не обрабатывает бизнес-запросы S -> XXI.
 * Не обрабатывает async-ответы.
 */
@Component
@RequiredArgsConstructor
public class MiInternalRequestListener {

   private final MiInternalRequestDispatcher dispatcher;

//   @MITransportListener(queue = "mi-int.request")
//   public MiInternalResponse handleRequest(ReceivedMessage message) {
//      return dispatcher.dispatch(message);
//   }
}