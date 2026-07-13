package ru.inversion.msmev.mi.business;

import ru.inversion.mi.transport.payload.ReceivedPayload;

public record MiBusinessPayload (
   ReceivedPayload source,
   String contentType,
   long size,
   boolean fromS3
)
{
   public boolean empty()
   {
      return source == null || size <= 0;
   }
}