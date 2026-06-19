package ru.inversion.msmev.transport;

import ru.inversion.utils.IDumpable;
import ru.inversion.utils.U;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Результат успешной публикации сообщения в MI.
 * <p>
 * Нужен не как бизнес-результат, а как техническое подтверждение:
 * XXL может после него вызвать to_Sent.
 */
public record MiPublishReceipt(
   UUID   messageId,
   String requestQueue,
   String responseQueue,
   String correlationId,
   OffsetDateTime publishedAt
)
{ 
   public Map<String,Object> toMap() {
      return U.toMap( "message_id", messageId(), "request_queue", requestQueue(), "response_queue", responseQueue(), "mi_correlation_id", correlationId(), "published_at", publishedAt());
   }
}