package ru.inversion.msmev.mi.business;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;

/**
 * Dispatcher бизнес-запросов MI -> XXI.
 *
 * Зона ответственности:
 * - парсит ReceivedMessage в MiBusinessRequest;
 * - находит business handler;
 * - вызывает XXI API/handler для регистрации или обработки входящего запроса;
 * - формирует MiBusinessResponse;
 * - при бизнес-ошибке формирует error-response, а не просто падает.
 *
 * Не публикует ответ в очередь сам.
 */
@Component
@RequiredArgsConstructor
public class MiBusinessRequestDispatcher {

//   private final MiBusinessRequestParser parser;
//   private final MiBusinessRequestHandlerRegistry handlerRegistry;
//   private final MiBusinessExceptionMapper exceptionMapper;
//
//   public MiBusinessResponse dispatch(ReceivedMessage message) {
//      try {
//         MiBusinessRequest request = parser.parse(message);
//         MiBusinessRequestHandler handler = handlerRegistry.get(request.requestType());
//
//         return handler.handle(request);
//
//      } catch (Throwable e) {
//         return exceptionMapper.toBusinessResponse(message, e);
//      }
//   }
}

