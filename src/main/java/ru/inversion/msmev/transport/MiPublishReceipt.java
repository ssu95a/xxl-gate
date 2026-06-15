package ru.inversion.msmev.transport;

import java.time.OffsetDateTime;
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
{ }