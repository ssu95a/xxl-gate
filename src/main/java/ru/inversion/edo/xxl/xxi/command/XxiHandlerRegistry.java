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
 * <h5>Registry handler'ов команд XXI -> XXL.</h5>
 * <p>
 * Зона ответственности:
 * <ul>
 *    <li>Индексирует XxiCommandHandler по wsp_id;
 *    <li>Проверяет дубли wsp_id при старте приложения;
 *    <li>Возвращает нужный handler для XxiCommandDispatcher;
 *    <li>Бросает UNSUPPORTED_INF_ID, если handler не найден.
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class XxiHandlerRegistry {

   private final List<XxiCommandHandler> handlers;

   private Map<Integer, XxiCommandHandler> wspMap;

   /* Обработчики для команд из ЦАБС */
   private Map<XxiCommandKey, XxiDirectCommandHandler> directMap;

   @PostConstruct
   void init( )
   {
      Map<Integer, XxiCommandHandler> m = new HashMap<>();
      Map<XxiCommandKey, XxiDirectCommandHandler> d = new HashMap<>();

      for( XxiCommandHandler handler : handlers ) {
         XxiCommandHandler previous = m.put(handler.wspId(), handler);

         if( previous != null )
         {
            // Контроль дублирования обработчика, для wsp
            throw Errors.config (
                 "Duplicate XxiCommandHandler for wsp_id=" + handler.wspId(),
                 U.toMap("wsp_id", handler.wspId(), "handler_1", previous.getClass().getName(), "handler_2", handler.getClass().getName())
            );
         }

         if (!(handler instanceof XxiDirectCommandHandler directHandler))
            continue;

         Set<XxiCommandKey> commands = directHandler.commands();

         if( commands == null || commands.isEmpty())
             throw Errors.config("Direct command handler has no commands", U.toMap("handler", handler.getClass().getName()));

         for( XxiCommandKey command : commands )
         {
            XxiDirectCommandHandler old = d.put(command, directHandler);

            if( old != null )
            {
               throw Errors.config (
                 "Duplicate direct XXI command handler",
                 U.toMap( "inf_id", command.infId(), "action", command.action(), "handler_1", old.getClass().getName(), "handler_2", handler.getClass().getName() )
               );
            }
         }
      }
      wspMap = Map.copyOf(m);
      directMap = Map.copyOf(d);

      handlers.clear();
   }

   /** */
   public XxiCommandHandler getXxiCommandHandler(int wspId)
   {
      XxiCommandHandler handler = wspMap.get(wspId);

      if( handler == null )
         throw Errors.unsupportedWsp (
              "Обработчик [XxiCommandHandler] не найден для wsp_id=" + wspId,
              U.toMap( "wsp_id", wspId, "known_wsp_ids", wspMap.keySet() )
         );

      return handler;
   }

   /** */
   public XxiDirectCommandHandler getDirectHandler( XxiCommandKey command )
   {
      XxiDirectCommandHandler handler = directMap.get(command);

      if( handler == null )
         throw Errors.contract(
           "Unsupported direct XXI command",
           U.toMap( "inf_id", command.infId(), "action", command.action(), "known_commands", directMap.keySet() )
         );

      return handler;
   }
}