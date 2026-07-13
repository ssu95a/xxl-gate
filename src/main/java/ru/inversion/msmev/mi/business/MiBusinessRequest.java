package ru.inversion.msmev.mi.business;


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
   MiBusinessPayload  payload,
   Map<String, Object> attributes
)
{
   public MiBusinessRequest
   {
      attributes =
              attributes == null || attributes.isEmpty()
                      ? Map.of()
                      : Map.copyOf(attributes);
   }
}