package ru.inversion.msmev.mi.business;

import ru.inversion.mi.transport.payload.ReceivedPayload;

import java.io.IOException;
import java.io.InputStream;

/**
 * Payload бизнес-запроса MI -> XXI.
 * <p>
 * Тонкая business-прокладка над transport payload.
 */
public record MiBusinessPayload( ReceivedPayload source )
{
   public String contentType()
   {
      return source == null ? null : source.contentType();
   }

   public long size()
   {
      return source == null ? 0 : source.size();
   }

   public InputStream openStream() throws IOException
   {
      if( source == null )
          throw new IOException("MI business payload is empty");

      return source.openStream();
   }
}