package ru.inversion.edo.xxl.xxi.command;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.utils.U;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <h5>Реестр обработчиков XXI -> XXL.</h5>
 * <p>
 *    Хранит и дает доступ к двум видам обработчиков:
 *    1. Обычные send-handler'ы индексируются по wsp_id.
 *    2. Direct-команды индексируются по паре (inf_id, action).
 */
@Component
@RequiredArgsConstructor
public class XxiHandlerRegistry
{

   private record HandlerKey(int wspId, Integer infId) {}

   /*
    * Все Spring-компоненты, являющиеся XxiCommandHandler + XxiDirectCommandHandler.
    */
   private final List<XxiCommandHandler> handlers;
   private final List<XxiDirectCommandHandler> directHandlers;

   private Map<HandlerKey, XxiCommandHandler> handlerMap;
   private Map<XxiCommandKey, XxiDirectCommandHandler> directMap;

   @PostConstruct
   void init()
   {
      handlerMap = buildHandlerMap(handlers);
      directMap  = buildDirectMap(directHandlers);
   }

   /**
    * Индексация обычных send-handler'ов по wsp_id.
    */
   private Map<HandlerKey, XxiCommandHandler> buildHandlerMap(
           List<XxiCommandHandler> handlers)
   {
      final Map<HandlerKey, XxiCommandHandler> result = new HashMap<>();

      for(XxiCommandHandler handler : handlers)
      {
         if(handler == null)
            throw Errors.config(
                    "XxiCommandHandler list contains null",
                    Map.of()
            );

         HandlerKey key =
                 new HandlerKey(handler.wspId(), handler.infId());

         XxiCommandHandler previous = result.put(key, handler);

         if(previous != null)
         {
            throw Errors.config(
                    "Duplicate XxiCommandHandler",
                    U.toMap(
                            "wsp_id", key.wspId(),
                            "inf_id", key.infId(),
                            "handler_1", previous.getClass().getName(),
                            "handler_2", handler.getClass().getName()
                    )
            );
         }
      }

      return Map.copyOf(result);
   }

   /**
    * Индексация direct-команд по паре:
    * <p>
    * inf_id + action
    * inf_id == null означает глобальную команду XXL.
    */
   private Map<XxiCommandKey, XxiDirectCommandHandler> buildDirectMap( List<XxiDirectCommandHandler> handlers )
   {
      final Map<XxiCommandKey, XxiDirectCommandHandler> result = new HashMap<>();

      for( XxiDirectCommandHandler handler : handlers )
      {
         if( handler == null )
             throw Errors.config( "XxiDirectCommandHandler list contains null", Map.of() );

         Set<XxiCommandKey> commands = handler.commands();

         if( commands == null || commands.isEmpty() )
         {
            continue;
            /*
            throw Errors.config (
              "Direct command handler has no commands",
              U.toMap( "handler", handler.getClass().getName() )
            );
            */
         }

         for( XxiCommandKey command : commands )
         {
            if(command == null)
            {
               throw Errors.config(
                 "Direct command handler contains null command",
                 U.toMap( "handler", handler.getClass().getName() )
               );
            }

            XxiDirectCommandHandler previous = result.put( command, handler );

            if( previous != null )
            {
               throw Errors.config (
                       "Duplicate direct XXI command handler",
                       U.toMap (
                            "inf_id", command.infId(),
                            "action", command.action(),
                            "handler_1", previous.getClass().getName(),
                            "handler_2", handler.getClass().getName()
                       )
               );
            }
         }
      }

      return Map.copyOf(result);
   }


   /**
    * Handler обычного action=send.
    */
   public XxiCommandHandler getXxiCommandHandler(int wspId, int infId)
   {
      /* Сначала специализированный handler конкретного ВС. */
      XxiCommandHandler handler = handlerMap.get( new HandlerKey(wspId, infId) );

      if( handler != null )
          return handler;

      /* Затем handler по умолчанию для WSP. */
      handler = handlerMap.get( new HandlerKey(wspId, null) );

      if( handler != null )
          return handler;

      throw Errors.unsupportedWsp( "Обработчик [XxiCommandHandler] не найден", U.toMap( "wsp_id", wspId, "inf_id", infId, "known_handlers", handlerMap.keySet() ) );
   }


   /**
    * Handler direct-команды.
    */
   public XxiDirectCommandHandler getDirectHandler( XxiCommandKey command )
   {
      if( command == null )
          throw Errors.contract( "Direct XXI command key is null" );

      XxiDirectCommandHandler handler = directMap.get(command);

      if(handler == null)
      {
         throw Errors.contract (
              "Unsupported direct XXI command",
              U.toMap(
                   "inf_id", command.infId(),
                   "action", command.action(),
                   "known_commands", directMap.keySet()
              )
         );
      }

      return handler;
   }
}