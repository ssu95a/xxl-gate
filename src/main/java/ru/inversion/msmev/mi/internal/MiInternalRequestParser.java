package ru.inversion.msmev.mi.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.msmev.error.Errors;

import java.time.OffsetDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
public final class MiInternalRequestParser
{
   private final ObjectMapper objectMapper;

   public MiInternalRequest parse(
           ReceivedMessage message
   )
   {
      if( message == null )
      {
         throw Errors.miServiceBadFormat(
                 "MI query message is null",
                 Map.of()
         );
      }

      byte[] payload = message.getFileData();

      if( payload == null || payload.length == 0 )
      {
         throw Errors.miServiceBadFormat(
                 "MI query payload is empty",
                 Map.of(
                         "request_id",
                         message.getRequestId()
                 )
         );
      }

      try
      {
         MiInternalQuery query =
                 objectMapper.readValue(
                         payload,
                         MiInternalQuery.class
                 );

         if( query.queryType() == null
                 || query.queryType().isBlank() )
         {
            throw Errors.miServiceBadFormat(
                    "MI queryType is empty",
                    Map.of(
                            "request_id",
                            message.getRequestId()
                    )
            );
         }

         return new MiInternalRequest(
                 message.getRequestId(),
                 query.queryType().trim(),
                 query.params(),
                 message.getCreatedAt(),
                 message.getSourceSystem(),
                 message.getSourceVersion()
         );
      }
      catch( RuntimeException exception )
      {
         throw exception;
      }
      catch( Exception exception )
      {
         throw Errors.miServiceBadFormat(
                 "Failed to parse MI query payload",
                 Map.of(
                         "request_id",
                         message.getRequestId()
                 )
         );
      }
   }
}