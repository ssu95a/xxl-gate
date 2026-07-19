package ru.inversion.msmev.mi.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.mi.transport.payload.ReceivedPayload;
import ru.inversion.msmev.error.Errors;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public final class MiInternalRequestParser
{
   private final ObjectMapper objectMapper;

   public MiInternalRequest parse ( ReceivedMessage message  )
   {
      validateMessage(message);

      final ReceivedPayload payload = message.getPayload();

      final MiInternalQuery query = readQuery(message, payload);

      validateQuery(message, query);

      return new MiInternalRequest (
         message.getRequestId(),
         query.queryType().trim(),
         query.params(),
         message.getCreatedAt(),
         message.getSourceSystem(),
         message.getSourceVersion()
      );
   }

   /** */
   private void validateMessage( ReceivedMessage message )
   {
      if( message == null )
         throw Errors.miServiceBadFormat (
           "MI query message is null",
           Map.of()
         );

      if( message.getRequestId() == null )
         throw Errors.miServiceBadFormat(
            "MI query requestId is null",
            parameters(message)
         );

//      if( message.getPayload() == null )
//          throw Errors.miServiceBadFormat( "MI query payload is null", parameters(message) );

//      if( message.getPayload().size() == 0L )
//          throw Errors.miServiceBadFormat( "MI query payload is empty", parameters(message) );
   }

   /** */
   private MiInternalQuery readQuery( ReceivedMessage message, ReceivedPayload payload )
   {
      /*
       * openStream() должен отдавать новый поток при каждом вызове.
       */
      try( InputStream stream = payload.openStream() )
      {
         return objectMapper.readValue( stream, MiInternalQuery.class );
      }
      catch( JsonProcessingException exception )
      {
         throw Errors.miServiceBadFormat( "MI query payload contains invalid JSON", parameters(message) );
      }
      catch( IOException exception )
      {
         throw Errors.technicalBreak( "Failed to read MI query payload", exception, parameters(message) );
      }
      catch( RuntimeException exception )
      {
         throw Errors.technicalBreak( "Unexpected error while reading MI query payload", exception, parameters(message) );
      }
   }

   /** */
   private void validateQuery( ReceivedMessage message, MiInternalQuery query )
   {
      if( query == null )
          throw Errors.miServiceBadFormat( "MI query payload contains null", parameters(message) );

      if( query.queryType() == null || query.queryType().isBlank() )
          throw Errors.miServiceBadFormat( "MI queryType is empty", parameters(message) );
   }

   /** */
   private Map<String, Object> parameters( ReceivedMessage message )
   {
      Map<String, Object> result = new LinkedHashMap<>();

      if( message == null )
          return result;

      result.put(
              "request_id",
              message.getRequestId()
      );

      result.put(
              "correlation_id",
              message.getMiCorrelationId()
      );

      result.put(
              "source_system",
              message.getSourceSystem()
      );

      result.put(
              "source_version",
              message.getSourceVersion()
      );

      result.put(
              "created_at",
              message.getCreatedAt()
      );

      ReceivedPayload payload =
              message.getPayload();

      if( payload != null )
      {
         result.put(
                 "payload_content_type",
                 payload.contentType()
         );

         result.put(
                 "payload_size",
                 payload.size()
         );
      }

      return result;
   }
}