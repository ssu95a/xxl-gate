package ru.inversion.edo.xxl.xxi.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.xxi.protocol.XXLRequest;
import ru.inversion.edo.xxl.xxi.protocol.XXLResponse;
import ru.inversion.edo.xxl.error.XXLExceptionMapper;

/**
 * <h5>Dispatcher команд XXI -> XXL.</h5>
 * <p>
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

   private final XxiResponseHandler responseHandler;

   public XXLResponse dispatch( XXLRequest request ) {

      try {

         // Проверяем request из XXI, пришел из очереди multi-bus
         validator.validate( request );

         //Формируем пару ВС + команда
         final XxiCommandKey command = new XxiCommandKey( request.getInfId(), request.getAction() );

         // Если не отправка запроса,
         // то идем по другой ветке - direct command
         if( !XxiRequestValidator.ACTION_SEND.equals( command.action() ) )
         {
            XxiDirectCommandHandler handler = xxiHandlerRegistry.getDirectHandler(command);
            return handler.handleDirect( command, request );
         }

         // Если обрабатываем ответ на запрос, то идем в другой flow!
         // Здесь заканчиваем!
         if( "response".equalsIgnoreCase(request.getMessageType()) )
         {
            return responseHandler.send(request);
         }

         // Контекст для вызова обработчика запроса из XXI
         XxiCommandContext context = contextFactory.create( request );

         // Оцениваем данные пришедшие в запросе
         // + состояния реального объекта из БД.
         // Проверка статуса запросов
         XXLResponse response = context.makeResponseOrNull();

         //если статус не подходит для работы, сразу выходим
         if( response != null )
             return response;

         // если есть ошибки, то выходим по Exception
         context.checkSendAllowed( );

         final XxiCommandHandler handler = xxiHandlerRegistry.getXxiCommandHandler( context.wspId() );

         return handler.send(context);

      } catch (Exception e) {
         return exceptionMapper.toXXLResponse( e );
      }
   }
}