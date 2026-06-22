package ru.inversion.msmev.mi.response;

import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.msmev.error.Errors;
import ru.inversion.utils.U;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <h5>Async-ответ MI -> XXL. На ранее отправленный запрос из XXL -> MI</h5>
 * <p>
 * Зона ответственности:
 * <ul>
 *  <li>Хранит исходный transport message;
 *  <li>Хранит тип ответа: item-response или container-rejected;
 *  <li>Хранит business context, восстановленный из payload/outbox/XXI;
 *  <li>Не применяет ответ к XXI.
 * </ul>
 */
public record MiAsyncResponse(

     ReceivedMessage sourceMessage,

     MiAsyncResponseKind kind,

     Long reqId,

     UUID requestExternalUuid,
     UUID itemExternalUuid,

     Integer infId,
     Integer wspId,

     String responseCode,
     String responseInfo,
     String responseDetails,

     String rawPayload,

     OffsetDateTime receivedAt,

     Map<String, Object> attributes
)
{
   /** */
   public String requestId() {
      return sourceMessage.getRequestId();
   }

   public String originalRequestId() {
      return sourceMessage.getOriginalRequestId();
   }

   public String miCorrelationId() {
      return sourceMessage.getMiCorrelationId();
   }

   public long deliveryTag() {
      return sourceMessage.getDeliveryTag();
   }

   public Map<String, Object> parameters() {

      Map<String, Object> m = new HashMap<>(attributes);

      m.putAll(
         U.toMap (
           "kind", kind,
           "req_id", reqId,
           "external_uuid", requestExternalUuid,
           "item_external_uuid", itemExternalUuid,
           "inf_id", infId,
           "response_code", responseCode,
           "response_info", responseInfo
         )
      );

      return m;
   }
}