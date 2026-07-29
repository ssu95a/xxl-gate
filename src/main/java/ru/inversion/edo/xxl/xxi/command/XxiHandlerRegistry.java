package ru.inversion.edo.xxl.xxi.command;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.utils.U;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

   @PostConstruct
   void init( )
   {
      Map<Integer, XxiCommandHandler> m = new HashMap<>();

      for( XxiCommandHandler handler : handlers )
      {
          XxiCommandHandler previous = m.put( handler.wspId(), handler );

         if( previous != null )
         {
            // Контроль дублирования обработчика, для wsp
            throw Errors.config (
              "Duplicate XxiCommandHandler for wsp_id=" + handler.wspId(),
              U.toMap( "wsp_id", handler.wspId(), "handler_1", previous.getClass().getName(), "handler_2", handler.getClass().getName() )
            );
         }
      }

      wspMap = Map.copyOf(m);

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
}