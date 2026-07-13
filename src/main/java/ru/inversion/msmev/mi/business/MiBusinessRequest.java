package ru.inversion.msmev.mi.business;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record MiBusinessRequest(
   UUID messageId,
   UUID correlationId,
   String requestType,
   String infNamespace,
   OffsetDateTime createdAt,
   String sourceSystem,
   String sourceVersion,
   JsonNode payload,
   Map<String, Object> attributes
)
{ }