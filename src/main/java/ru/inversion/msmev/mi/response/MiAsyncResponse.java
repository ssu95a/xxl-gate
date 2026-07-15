package ru.inversion.msmev.mi.response;

import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.mi.transport.model.ErrorInfo;
import ru.inversion.mi.transport.model.MiAsyncItemResult;
import ru.inversion.mi.transport.model.MiAsyncResponseKind;
import ru.inversion.mi.transport.payload.ReceivedPayload;
import ru.inversion.msmev.util.Attrs;
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
      headers     = headers == null ? Map.of() : Collections.unmodifiableMap( headers );
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

   /** */
   public long deliveryTag()
   {
      return sourceMessage.getDeliveryTag();
   }


   /** */
   public boolean itemContainer()
   {
      return kind() == MiAsyncResponseKind.ITEM_RESULT || kind() == MiAsyncResponseKind.REQUEST_PART_ERROR;
   }


   /** */
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
      Attrs attrs = Attrs.create();

      attrs.putIfNotNull( "kind", kind() );
      attrs.putIfNotNull( "request_id", messageId());
      attrs.putIfNotNull( "original_request_id", originalRequestId());
      attrs.putIfNotNull( "mi_correlation_id", correlationId());

      attrs.putIfNotNull( "inf_id", infId );
      attrs.putIfNotNull( "inf_namespace", infNamespace() );

      attrs.putIfNotNull( "response_code", responseCode());
      attrs.putIfNotNull( "response_info", responseInfo());
      attrs.putIfNotNull( "occurred_at",   occurredAt());

      attrs.put( "item_count",  itemResults.size());
      attrs.put( "error_count", errors.size());

      return attrs.toMap();
   }

   /**
    * Диагностические параметры конкретного item.
    */
   public Map<String, Object> itemParameters( MiAsyncItemResult item, int itemIndex )
   {
      Attrs attrs = Attrs.create( parameters() );

      attrs.put( "item_index", itemIndex );

      if( item == null)
          return attrs.toMap();

      attrs.putIfNotNull(  "item_external_uuid", item.itemExternalUuid() );
      attrs.putIfNotNull(  "item_response_code", item.responseCode() );
      attrs.putIfNotNull(  "item_response_info", item.responseInfo() );
      attrs.putIfNotNull(  "item_occurred_at", item.occurredAt() );

      if( item.payload() != null) {
         attrs.put ( "item_payload_content_type", item.payload().contentType() );
         attrs.put ( "item_payload_size", item.payload().contentType() );
      }

      return attrs.toMap();
   }


   /** */
   public static Map<String,Object> messageParameters( ReceivedMessage message )
   {
      Attrs result = Attrs.create( );


      if( message == null ) {
          result.put("message", null);
          return result.toMap();
      }

      result.putIfNotNull("request_id", message.getRequestId());
      result.putIfNotNull("original_request_id", message.getOriginalRequestId());
      result.putIfNotNull("mi_correlation_id", message.getMiCorrelationId());
      result.putIfNotNull("response_kind", message.getResponseKind());
      result.putIfNotNull("response_code", message.getResponseCode());
      result.putIfNotNull("inf_id", message.getInfId());
      result.putIfNotNull("inf_namespace", message.getInfNamespace());
      result.putIfNotNull("file_name", message.getFileName());
      result.putIfNotNull("send_mode", message.getSendMode());
      result.putIfNotNull("source_system", message.getSourceSystem());
      result.putIfNotNull("source_version",message.getSourceVersion());
      result.putIfNotNull("created_at",    message.getCreatedAt());
      result.putIfNotNull("occurred_at",   message.getOccurredAt());
      result.putIfNotNull("from_s3",       message.isFromS3());
      result.putIfNotNull("delivery_tag",  message.getDeliveryTag());
      result.put( "item_count", message.getItemResults() == null ? 0 : message.getItemResults().size() );

      if( message.getPayload() != null )
      {
         result.put("payload_content_type", message.getPayload().contentType());
         result.put("payload_size", message.getPayload().size());
      }

      return result.toMap();
   }
}