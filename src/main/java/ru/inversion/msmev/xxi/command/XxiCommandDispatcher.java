package ru.inversion.msmev.xxi.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.msmev.dto.XXLRequest;
import ru.inversion.msmev.dto.XXLResponse;
import ru.inversion.msmev.error.XXLExceptionMapper;
import ru.inversion.msmev.xxi.handler.XxiHandlerRegistry;

/**
 * Dispatcher команд XXI -> XXL.
 *
 * Зона ответственности:
 * - валидирует входной XXLRequest;
 * - создаёт XxiCommandContext;
 * - обрабатывает идемпотентные состояния mi_req;
 * - находит handler по wsp_id;
 * - вызывает handler.send(context);
 * - маппит все Throwable в XXLResponse.
 * Не делает:
 * - take_For_Proc;
 * - to_Sent;
 * - to_Error;
 * - сборку payload;
 * - вызов MI/S;
 * - применение ответов.
 * Эти действия принадлежат конкретному XxiCommandHandler.
 * @see XxiCommandHandler
 */
@Component
@RequiredArgsConstructor
public class XxiCommandDispatcher {

   private final XxiRequestValidator validator;
   private final XxiHandlerRegistry  xxiHandlerRegistry;

   private final XxiCommandContextFactory contextFactory;

   private final XXLExceptionMapper exceptionMapper;

   public XXLResponse dispatch( XXLRequest request ) {

      try {

         // Проверяем request из XXI, пришел из очереди multi-bus
         validator.validate( request );

         // Контекст для вызова обработчика запроса из XXI
         XxiCommandContext context = contextFactory.create( request );

         // Оцениваем данные пришедшие в запросе
         // + состояния реального объекта из БД.
         // Проверка статуса запросов
         XXLResponse response = context.makeResponseOrNull();

         if( response != null )
             return response;

         context.checkSendAllowed( );

         final XxiCommandHandler handler = xxiHandlerRegistry.getXxiCommandHandler( context.wspId() );

         return handler.send(context);

      } catch (Throwable throwable) {
         return exceptionMapper.toXXLResponse( throwable );
      }
   }
}