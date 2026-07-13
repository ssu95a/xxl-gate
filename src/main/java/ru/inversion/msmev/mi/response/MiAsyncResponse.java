package ru.inversion.msmev.mi.response;

import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.mi.transport.model.ErrorInfo;
import ru.inversion.mi.transport.model.MiAsyncItemResult;
import ru.inversion.mi.transport.model.MiAsyncResponseKind;
import ru.inversion.mi.transport.payload.ReceivedPayload;
import ru.inversion.utils.Checks;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * <h5>Нормализованный асинхронный ответ MI-edo -> XXL.</h5>
 * <p>
 * Один MiAsyncResponse соответствует одному ReceivedMessage
 * и одному контейнеру ответа.
 * <p>
 * Для ITEM_RESULT контейнер содержит от одного до нескольких
 * элементов в itemResults.
 */
public record MiAsyncResponse(

     ReceivedMessage sourceMessage,

     Integer infId,

     List<MiAsyncItemResult> itemResults,
     List<ErrorInfo> errors,

     Map<String, Object> headers
)
{
   /**
    * Нормализуем коллекции, чтобы handlers и repositories
    * не проверяли их на null и не могли менять контейнер.
    */
   public MiAsyncResponse
   {
      Checks.Require.object( sourceMessage, "sourceMessage" );

      itemResults = itemResults == null ? List.of() : List.copyOf(itemResults);
      errors      = errors == null ? List.of() : List.copyOf(errors);
      headers     = headers == null ? Map.of() : Collections.unmodifiableMap( new LinkedHashMap<>(headers) );
   }

   /** Тип контейнера */
   public MiAsyncResponseKind kind()
   {
      return sourceMessage.getResponseKind();
   }

   /** Уникальный идентификатор входящего ответа MI-edo -> XXL. */
   public UUID messageId()
   {
      return sourceMessage.getRequestId();
   }

   /** externalRequestUuid исходного запроса XXL -> MI-edo. */
   public UUID originalRequestId()
   {
      return sourceMessage.getOriginalRequestId();
   }

   /** */
   public UUID correlationId()
   {
      return sourceMessage.getMiCorrelationId();
   }

   /** */
   public String infNamespace()
   {
      return sourceMessage.getInfNamespace();
   }

   /** Коды возврата */
   public String responseCode()
   {
      return sourceMessage.getResponseCode();
   }
   public String responseInfo()
   {
      return sourceMessage.getResponseInfo();
   }
   public String responseDetails()
   {
      return sourceMessage.getResponseDetails();
   }


   public OffsetDateTime occurredAt()
   {
      return sourceMessage.getOccurredAt();
   }

   public OffsetDateTime createdAt()
   {
      return sourceMessage.getCreatedAt();
   }

   /**
    * Payload уровня запроса или всего контейнера.
    * <p>
    * Payload отдельного item находится в MiAsyncItemResult.
    */
   public ReceivedPayload payload()
   {
      return sourceMessage.getPayload();
   }

   /** */
   public long deliveryTag()
   {
      return sourceMessage.getDeliveryTag();
   }

   public boolean itemContainer()
   {
      return kind() == MiAsyncResponseKind.ITEM_RESULT;
   }

   public boolean requestLevelResponse()
   {
      return kind() == MiAsyncResponseKind.REQUEST_REJECTED || kind() == MiAsyncResponseKind.REQUEST_FAILED;
   }

   public int itemCount()
   {
      return itemResults.size();
   }

   /**
    * Параметры всего контейнера.
    * Для логов и exception
    */
   public Map<String, Object> parameters()
   {
      Map<String, Object> result = new LinkedHashMap<>();

      put( result, "kind", kind() );
      put( result, "request_id", messageId());
      put( result, "original_request_id", originalRequestId());
      put( result, "mi_correlation_id", correlationId());

      put( result, "inf_id", infId );
      put( result, "inf_namespace", infNamespace() );

      put(result, "response_code", responseCode());
      put(result, "response_info", responseInfo());
      put(result, "occurred_at", occurredAt());

      result.put( "item_count", itemResults.size());
      result.put( "error_count", errors.size());

      addPayloadParameters( result, "payload", payload() );

      return result;
   }

   /**
    * Диагностические параметры конкретного item.
    */
   public Map<String, Object> itemParameters(
           MiAsyncItemResult item,
           int itemIndex
   ) {
      Map<String, Object> result = new LinkedHashMap<>(parameters());

      result.put("item_index", itemIndex);

      if (item == null)
         return result;

      put( result, "item_external_uuid", item.itemExternalUuid() );
      put( result, "item_response_code", item.responseCode() );
      put( result, "item_response_info", item.responseInfo() );
      put( result, "item_occurred_at", item.occurredAt() );

      addPayloadParameters( result, "item_payload", item.payload() );

      return result;
   }

   /** Добавление информации о payload */
   private static void addPayloadParameters( Map<String, Object> target, String prefix, ReceivedPayload payload )
   {
      if (payload == null)
         return;
      put( target, prefix + "_content_type", payload.contentType() );
      target.put( prefix + "_size", payload.size() );
   }

   private static void put( Map<String, Object> target, String name, Object value )
   {
      if (value != null)
         target.put(name, value);
   }

   /** */
   public static Map<String,Object> messageParameters( ReceivedMessage message )
   {
      Map<String, Object> result = new LinkedHashMap<>();

      if( message == null ) {
          result.put("message", null);
          return result;
      }

      result.put("request_id", message.getRequestId());
      result.put("original_request_id", message.getOriginalRequestId());
      result.put("mi_correlation_id", message.getMiCorrelationId());
      result.put("response_kind", message.getResponseKind());
      result.put("response_code", message.getResponseCode());
      result.put("inf_id", message.getInfId());
      result.put("inf_namespace", message.getInfNamespace());
      result.put("file_name", message.getFileName());
      result.put("send_mode", message.getSendMode());
      result.put("source_system", message.getSourceSystem());
      result.put("source_version", message.getSourceVersion());
      result.put("created_at", message.getCreatedAt());
      result.put("occurred_at", message.getOccurredAt());
      result.put("from_s3", message.isFromS3());
      result.put("delivery_tag", message.getDeliveryTag());
      result.put( "item_count", message.getItemResults() == null ? 0 : message.getItemResults().size() );

      if( message.getPayload() != null )
      {
         result.put("payload_content_type", message.getPayload().contentType());
         result.put("payload_size", message.getPayload().size());
      }

      return result;
   }
}