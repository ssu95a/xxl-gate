package ru.inversion.msmev.mi;

import ru.inversion.mi.transport.MiTransportSendMode;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Базовый интерфейс конверта сообщения XXL -> MI.
 */
public interface IMIEnvelope {

   /**
    * Версия envelope-контракта.
    */
   String version();

   /**
    * Дата/время создания сообщения XXL.
    */
   OffsetDateTime createdAt();

   /**
    * Режим отправки через MI transport.
    */
   MiTransportSendMode sendMode();

   /**
    * Идентификаторы сообщения и бизнес-контейнера.
    */
   Ids ids();

   /**
    * Источник сообщения.
    */
   Source source();

   /**
    * Routing-настройки сообщения.
    */
   Route route();

   /**
    * Дополнительные transport/application headers.
    */
   Headers headers();

   /**
    * Бизнес payload.
    */
   Payload payload();

   /**
    * Namespace вида сведений.
    */
   String infNamespace();

   /**
    * Идентификаторы.
    */
   interface Ids {

      /**
       * ID конкретного сообщения
       */
      UUID messageId();

      /**
       * ID вызова XXI -> XXL.
       */
      UUID callUuid();

      /**
       * Внешний глобальный ID контейнера запроса.
       */
      UUID externalRequestUuid();

      /**
       * Внешний глобальный ID запроса на который дается ответ.
       * Используется при обработке ответов на ранее отправленный запрос
       */
      UUID originalRequestUuid();

      /**
       * Корреляционный ID бизнес-цепочки.
       */
      UUID correlationId();

      /**
       * Внутренний ID запроса в XXI.
       */
      long reqId();

      /**
       * ID вида сведений.
       */
      int infId();

      /**
       * ID приложения
       */
      int wspId();
   }

   /**
    * Источник сообщения.
    */
   interface Source {

      /**
       * Система-источник.
       * Например: XXL.
       */
      String name();

      /**
       * Модуль/компонент-источник.
       * Например: xxi-command, mi-0007-handler.
       */
      String module();

      /**
       * Экземпляр приложения/host/pod.
       */
      default String instance() {
         return null;
      }
   }

   /**
    * Routing для transport-слоя.
    */
   interface Route {

      /**
       * Очередь request в MI.
       */
      String requestQueue();

      /**
       * Очередь, куда MI должен вернуть async response.
       */
      String responseQueue();

      /**
       * TTL сообщения в миллисекундах.
       * null, если transport использует default.
       */
      Long ttlMs();
   }

   /**
    * Headers сообщения.
    */
   interface Headers {

      /**
       * Получить значение header.
       */
      Optional<Object> get(String header);

      /**
       * Присутствует ли header.
       */
      boolean contains(String header);

      /**
       * Read-only view всех headers.
       */
      Map<String, Object> asMap();
   }

   /**
    * Payload сообщения.
    */
   interface Payload {

      /**
       * MIME type payload.
       * Например: application/xml.
       */
      String contentType();

      /**
       * Данные payload.
       */
      Object data();

      /**
       * Класс данных payload.
       */
      Class<?> dataClass();

      /**
       * Размер данных.
       * -1, если неизвестно.
       */
      long dataSize();
   }
}