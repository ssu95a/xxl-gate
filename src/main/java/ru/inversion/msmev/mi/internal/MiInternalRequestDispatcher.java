package ru.inversion.msmev.mi.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;

/**
 * Dispatcher внутренних технических запросов MI -> XXL.
 *
 * Зона ответственности:
 * - парсит MiInternalRequest;
 * - находит internal service handler;
 * - вызывает handler;
 * - маппит ошибки в MiInternalResponse.
 *
 * Используется для технического взаимодействия:
 * - проверка лицензии;
 * - получение технических данных из XXI;
 * - health/config/info запросы MI.
 */
@Component
@RequiredArgsConstructor
public class MiInternalRequestDispatcher {

   //private final MiInternalRequestParser parser;
   private final MiInternalServiceRegistry registry;
   //private final MiInternalExceptionMapper exceptionMapper;

//   public MiInternalResponse dispatch(ReceivedMessage message) {
//      try {
//         MiInternalRequest request = parser.parse(message);
//         MiInternalServiceHandler handler = registry.get(request.serviceCode());
//
//         return handler.handle(request);
//
//      } catch (Throwable e) {
//         return exceptionMapper.toInternalResponse(message, e);
//      }
//   }
}