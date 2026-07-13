package ru.inversion.msmev.mi.response;

import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.mi.transport.model.MiAsyncItemResult;
import ru.inversion.msmev.error.Errors;
import ru.inversion.utils.Checks;
import ru.inversion.utils.S;
import ru.inversion.utils.U;
import ru.inversion.mi.transport.payload.ReceivedPayload;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <h5>Преобразует транспортный ReceivedMessage в контейнерный MiAsyncResponse.</h5>
 * <p>
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
   public MiAsyncResponse parse( ReceivedMessage message )
   {
      validateCommon( message );

      List<MiAsyncItemResult> itemResults = U.nvl( message.getItemResults(), Collections.emptyList() );

      switch( message.getResponseKind() )
      {
         case ITEM_RESULT:
            validateItemContainer( message, itemResults );
         break;
         case REQUEST_REJECTED:
         case REQUEST_FAILED:
            validateRequestLevelResponse( message, itemResults );
         break;
         default:
            throw badFormat( message, "Unsupported responseKind: " + message.getResponseKind(), null );
      }

      return new MiAsyncResponse (
         message,
         message.getInfId(),
         itemResults,
         message.getErrors() == null ? Collections.emptyList() : message.getErrors(),
         message.getHeaders()
      );
   }

   /**
    * Общие обязательные поля для любого ответа.
    */
   private void validateCommon(ReceivedMessage message)
   {
      if( message == null )
         throw Errors.miResponseBadFormat( "ReceivedMessage is null", Collections.emptyMap() );

      if( message.getRequestId() == null )
         throw badFormat( message, "requestId is null", null );

      if( message.getOriginalRequestId() == null )
         throw badFormat( message, "originalRequestId is null", null );

      if( message.getMiCorrelationId() == null )
          throw badFormat( message, "miCorrelationId is null", null );

      if( message.getResponseKind() == null )
         throw badFormat( message, "responseKind is null", null );

//      if( S.isNullOrEmpty(message.getInfNamespace()))
//          throw badFormat( message, "infNamespace is empty", null );
//
//      if( message.getCreatedAt() == null )
//         throw badFormat( message, "createdAt is null", null );

      validateHeaders( message, message.getHeaders(), null );
   }


   /**
    * ITEM_RESULT всегда является контейнером
    * с одним или несколькими элементами.
    */
   private void validateItemContainer( ReceivedMessage message, List<MiAsyncItemResult> items )
   {
      if( items.isEmpty() )
          throw badFormat( message, "ITEM_RESULT container is empty", null );

      for( int index = 0; index < items.size(); index++)
      {
           final MiAsyncItemResult item = items.get(index);
           validateItem( message, item, index );
      }
   }


   /** Проверка одного элемента из payload */
   private void validateItem( ReceivedMessage message, MiAsyncItemResult item, int index )
   {
      if( item == null )
          throw badFormat( message, "itemResults contains null item", U.toMap( "item_index", index ) );

      if( item.itemExternalUuid() == null )
          throw badFormat( message, "itemExternalUuid is null", itemParameters( item, index ) );

      if( S.isNullOrEmpty(item.responseCode()) )
          throw badFormat( message, "item responseCode is empty", itemParameters( item, index ) );

      if( item.occurredAt() == null )
          throw badFormat( message, "item occurredAt is null", itemParameters( item, index ) );

      validatePayload( message, item.payload(), index );

      validateHeaders( message, item.headers(), index );
   }

   /**
    * REQUEST_REJECTED и REQUEST_FAILED
    * относятся ко всему исходному запросу.
    */
   private void validateRequestLevelResponse (
      ReceivedMessage message,
      List<MiAsyncItemResult> items
   )
   {
      if( !items.isEmpty() )
         throw badFormat( message,
           "Request-level response must not contain itemResults",
            U.toMap( "item_count", items.size() )
         );

      if( S.isNullOrEmpty( message.getResponseCode() ) )
          throw badFormat( message, "responseCode is empty", null );

      if( message.getOccurredAt() == null )
         throw badFormat( message, "occurredAt is null", null );
   }


   /**
    * Сам payload parser не открывает.
    * Проверяется только его descriptor.
    */
   private void validatePayload( ReceivedMessage message, ReceivedPayload payload, Integer itemIndex )
   {
      if( payload == null)
          return;

      if( S.isNullOrEmpty( payload.contentType() ) )
          throw badFormat ( message, "payload.contentType is empty", U.toMap( "itemIndex", itemIndex ) );
   }

   /** */
   private void validateHeaders(
      ReceivedMessage message,
      Map<String, Object> headers,
      Integer itemIndex
   )
   {
      if( headers == null || headers.isEmpty() )
          return;
      //stub
      return;
   }


   /** */
   private Map<String, Object> itemParameters( MiAsyncItemResult item, int index )
   {
      return U.toMap (
         "item_index", index,
         "item_external_uuid", item.itemExternalUuid(),
         "item_response_code", item.responseCode()
      );
   }


   /** */
   private RuntimeException badFormat( ReceivedMessage message, String details, Map<String, Object> parameters )
   {
      Map<String, Object> result = MiAsyncResponse.messageParameters(message);

      if( parameters != null )
          result.putAll( parameters );

      return Errors.miResponseBadFormat( details, result );
   }
}