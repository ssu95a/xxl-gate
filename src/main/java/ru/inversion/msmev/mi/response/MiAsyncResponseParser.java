package ru.inversion.msmev.mi.response;

import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.mi.transport.model.MiAsyncItemResult;
import ru.inversion.msmev.error.Errors;
import ru.inversion.utils.U;
import ru.inversion.mi.transport.payload.ReceivedPayload;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Преобразует транспортный ReceivedMessage
 * в контейнерный MiAsyncResponse.
 *
 * Не читает payload.
 * Не анализирует rawMessageBody.
 * Не определяет тип ответа по содержимому.
 * Не применяет ответ к XXI.
 */
@Component
public class MiAsyncResponseParser {

   /**
    * Один ReceivedMessage преобразуется
    * в один MiAsyncResponse-контейнер.
    */
   public MiAsyncResponse parse(ReceivedMessage message)
   {
      validateCommon(message);

      List<MiAsyncItemResult> itemResults =
              safeList(message.getItemResults());

      switch (message.getResponseKind()) {
         case ITEM_RESULT:
            validateItemContainer(
                    message,
                    itemResults
            );
            break;

         case REQUEST_REJECTED:
         case REQUEST_FAILED:
            validateRequestLevelResponse(
                    message,
                    itemResults
            );
            break;

         default:
            throw badFormat(
                    message,
                    "Unsupported responseKind: "
                            + message.getResponseKind(),
                    null
            );
      }

      return new MiAsyncResponse(
              message,

              parseInfId(message),

              itemResults,
              safeList(message.getErrors()),

              message.getHeaders(),
              baseAttributes(message)
      );
   }

   /**
    * Общие обязательные поля для любого ответа.
    */
   private void validateCommon(ReceivedMessage message)
   {
      if (message == null) {
         throw Errors.miResponseBadFormat(
                 "ReceivedMessage is null",
                 Collections.emptyMap()
         );
      }

      if (message.getRequestId() == null) {
         throw badFormat(
                 message,
                 "requestId is null",
                 null
         );
      }

      if (message.getOriginalRequestId() == null) {
         throw badFormat(
                 message,
                 "originalRequestId is null",
                 null
         );
      }

      if (message.getMiCorrelationId() == null) {
         throw badFormat(
                 message,
                 "miCorrelationId is null",
                 null
         );
      }

      if (message.getResponseKind() == null) {
         throw badFormat(
                 message,
                 "responseKind is null",
                 null
         );
      }

      if (isBlank(message.getInfId())) {
         throw badFormat(
                 message,
                 "infId is empty",
                 null
         );
      }

      if (isBlank(message.getInfNamespace())) {
         throw badFormat(
                 message,
                 "infNamespace is empty",
                 null
         );
      }

      if (message.getCreatedAt() == null) {
         throw badFormat(
                 message,
                 "createdAt is null",
                 null
         );
      }

      validateHeaders(
              message,
              message.getHeaders(),
              null
      );
   }

   /**
    * ITEM_RESULT всегда является контейнером
    * с одним или несколькими результатами.
    */
   private void validateItemContainer(
           ReceivedMessage message,
           List<MiAsyncItemResult> items
   ) {
      if (items.isEmpty()) {
         throw badFormat(
                 message,
                 "ITEM_RESULT container is empty",
                 null
         );
      }

      /*
       * Корневой itemExternalUuid не используем.
       * Источником истины является itemResults.
       *
       * Пока transport не удалил это legacy-поле,
       * его наличие не считаем ошибкой.
       */

      for (int index = 0; index < items.size(); index++) {
         MiAsyncItemResult item =
                 items.get(index);

         validateItem(
                 message,
                 item,
                 index
         );
      }
   }

   private void validateItem(
           ReceivedMessage message,
           MiAsyncItemResult item,
           int index
   ) {
      if (item == null) {
         throw badFormat(
                 message,
                 "itemResults contains null item",
                 U.toMap(
                         "item_index",
                         index
                 )
         );
      }

      if (item.itemExternalUuid() == null) {
         throw badFormat(
                 message,
                 "itemExternalUuid is null",
                 itemParameters(
                         item,
                         index
                 )
         );
      }

      if (isBlank(item.responseCode())) {
         throw badFormat(
                 message,
                 "item responseCode is empty",
                 itemParameters(
                         item,
                         index
                 )
         );
      }

      if (item.occurredAt() == null) {
         throw badFormat(
                 message,
                 "item occurredAt is null",
                 itemParameters(
                         item,
                         index
                 )
         );
      }

      validatePayload(
              message,
              item.payload(),
              index
      );

      validateHeaders(
              message,
              item.headers(),
              index
      );
   }

   /**
    * REQUEST_REJECTED и REQUEST_FAILED
    * относятся ко всему исходному запросу.
    */
   private void validateRequestLevelResponse(
           ReceivedMessage message,
           List<MiAsyncItemResult> items
   ) {
      if (!items.isEmpty()) {
         throw badFormat(
                 message,
                 "Request-level response must not contain itemResults",
                 U.toMap(
                         "item_count",
                         items.size()
                 )
         );
      }

      if (isBlank(message.getResponseCode())) {
         throw badFormat(
                 message,
                 "responseCode is empty",
                 null
         );
      }

      if (message.getOccurredAt() == null) {
         throw badFormat(
                 message,
                 "occurredAt is null",
                 null
         );
      }

      validatePayload(
              message,
              message.getPayload(),
              null
      );
   }

   /**
    * Пока transport возвращает infId строкой,
    * преобразование остаётся на границе XXL.
    */
   private Integer parseInfId(ReceivedMessage message)
   {
      String value =
              trimToNull(message.getInfId());

      try {
         int infId =
                 Integer.parseInt(value);

         if (infId <= 0) {
            throw badFormat(
                    message,
                    "infId must be positive",
                    U.toMap(
                            "inf_id",
                            value
                    )
            );
         }

         return infId;

      } catch (NumberFormatException exception) {
         throw badFormat(
                 message,
                 "infId has invalid integer format",
                 U.toMap(
                         "inf_id",
                         value
                 )
         );
      }
   }

   /**
    * Сам payload parser не открывает.
    * Проверяется только его descriptor.
    */
   private void validatePayload(
           ReceivedMessage message,
           ReceivedPayload payload,
           Integer itemIndex
   ) {
      if (payload == null)
         return;

      if (isBlank(payload.contentType())) {
         throw badFormat(
                 message,
                 "payload.contentType is empty",
                 indexParameters(itemIndex)
         );
      }

      /*
       * -1 допускается как неизвестный размер.
       */
      if (payload.size() < -1L) {
         Map<String, Object> parameters =
                 indexParameters(itemIndex);

         parameters.put(
                 "payload_size",
                 payload.size()
         );

         throw badFormat(
                 message,
                 "payload.size is invalid",
                 parameters
         );
      }
   }

   private void validateHeaders(
           ReceivedMessage message,
           Map<String, Object> headers,
           Integer itemIndex
   ) {
      if (headers == null || headers.isEmpty())
         return;

      for (Map.Entry<String, Object> entry
              : headers.entrySet()) {

         if (isBlank(entry.getKey())) {
            throw badFormat(
                    message,
                    "headers contains empty key",
                    indexParameters(itemIndex)
            );
         }

         if (entry.getValue() == null) {
            Map<String, Object> parameters =
                    indexParameters(itemIndex);

            parameters.put(
                    "header",
                    entry.getKey()
            );

            throw badFormat(
                    message,
                    "headers contains null value",
                    parameters
            );
         }
      }
   }

   /**
    * Диагностические атрибуты контейнера.
    *
    * rawMessageBody намеренно не копируется,
    * так как он может быть объёмным.
    */
   private Map<String, Object> baseAttributes(
           ReceivedMessage message
   ) {
      Map<String, Object> result =
              new LinkedHashMap<>();

      if (message == null)
         return result;

      put(
              result,
              "request_id",
              message.getRequestId()
      );

      put(
              result,
              "original_request_id",
              message.getOriginalRequestId()
      );

      put(
              result,
              "mi_correlation_id",
              message.getMiCorrelationId()
      );

      put(
              result,
              "response_kind",
              message.getResponseKind()
      );

      put(
              result,
              "inf_id",
              message.getInfId()
      );

      put(
              result,
              "inf_namespace",
              message.getInfNamespace()
      );

      put(
              result,
              "file_name",
              message.getFileName()
      );

      put(
              result,
              "send_mode",
              message.getSendMode()
      );

      put(
              result,
              "xxl_version",
              message.getXxlVersion()
      );

      put(
              result,
              "source_system",
              message.getSourceSystem()
      );

      put(
              result,
              "source_version",
              message.getSourceVersion()
      );

      put(
              result,
              "created_at",
              message.getCreatedAt()
      );

      put(
              result,
              "occurred_at",
              message.getOccurredAt()
      );

      /*
       * Legacy-поле только для диагностики.
       * Для ITEM_RESULT источником истины
       * является itemResults.
       */
      put(
              result,
              "root_item_external_uuid",
              message.getItemExternalUuid()
      );

      result.put(
              "from_s3",
              message.isFromS3()
      );

      result.put(
              "delivery_tag",
              message.getDeliveryTag()
      );

      List<MiAsyncItemResult> items =
              message.getItemResults();

      result.put(
              "item_count",
              items == null
                      ? 0
                      : items.size()
      );

      if (message.getPayload() != null) {
         put(
                 result,
                 "payload_content_type",
                 message.getPayload().contentType()
         );

         result.put(
                 "payload_size",
                 message.getPayload().size()
         );
      }

      return result;
   }

   private Map<String, Object> itemParameters(
           MiAsyncItemResult item,
           int index
   ) {
      Map<String, Object> result =
              indexParameters(index);

      if (item == null)
         return result;

      put(
              result,
              "item_external_uuid",
              item.itemExternalUuid()
      );

      put(
              result,
              "item_response_code",
              item.responseCode()
      );

      return result;
   }

   private Map<String, Object> indexParameters(
           Integer itemIndex
   ) {
      Map<String, Object> result =
              new LinkedHashMap<>();

      if (itemIndex != null) {
         result.put(
                 "item_index",
                 itemIndex
         );
      }

      return result;
   }

   private RuntimeException badFormat(
           ReceivedMessage message,
           String details,
           Map<String, Object> parameters
   ) {
      Map<String, Object> result =
              baseAttributes(message);

      if (parameters != null)
         result.putAll(parameters);

      return Errors.miResponseBadFormat(
              details,
              result
      );
   }

   private <T> List<T> safeList(List<T> source)
   {
      return source == null
              ? Collections.emptyList()
              : source;
   }

   private boolean isBlank(String value)
   {
      return trimToNull(value) == null;
   }

   private String trimToNull(String value)
   {
      if (value == null)
         return null;

      String normalized =
              value.trim();

      return normalized.isEmpty()
              ? null
              : normalized;
   }

   private void put(
           Map<String, Object> target,
           String name,
           Object value
   ) {
      if (value != null)
         target.put(name, value);
   }
}