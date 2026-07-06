package ru.inversion.msmev.mi.internal;

import ru.inversion.mi.transport.payload.ReceivedPayload;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** */
public record MiInternalRequest(
   UUID messageId,
   String operation,
   String infNamespace,
   OffsetDateTime createdAt,
   ReceivedPayload payload,
   Map<String, Object> headers
)
{ }