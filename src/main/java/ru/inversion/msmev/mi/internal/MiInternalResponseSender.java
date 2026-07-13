package ru.inversion.msmev.mi.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.IMITransport;
import ru.inversion.mi.transport.ITransportRequest;
import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.mi.transport.TransportContainerRequest;
import ru.inversion.mi.transport.model.MiAsyncResponseKind;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.util.Attrs;

import java.time.OffsetDateTime;


@Component
@RequiredArgsConstructor
public final class MiInternalResponseSender
{
   public static final String RESPONSE_QUEUE = "mi-edo.xxl.queries.request-sync";

   private final IMITransport miTransport;
   private final ObjectMapper objectMapper;

   public void send ( ReceivedMessage requestMessage, MiInternalResult result )
   {
      final byte[] payload;

      try
      {
         payload = objectMapper.writeValueAsBytes( result.data() );
      }
      catch( Exception exception )
      {
         throw Errors.miServiceFailed (
              "Failed to build MI internal response payload",
              exception,
              fillAttrs(requestMessage, result).toMap()
         );
      }

      try
      {
         ITransportRequest response =
            TransportContainerRequest.builder()
             .queueName(RESPONSE_QUEUE)
             .originalRequestId( requestMessage == null ? null : requestMessage.getRequestId() )
             .responseKind     ( MiAsyncResponseKind.ITEM_RESULT )
             .responseCode     ( result.responseCode() )
             .responseCategory ( result.responseCategory() )
             .responseInfo     ( result.responseInfo() )
             .payload          ( payload )
             .mimeType         ( MediaType.APPLICATION_JSON )
             .sourceSystem     ("XXL"  )
             .sourceVersion    ("1.0.0")
             .createdAt        ( OffsetDateTime.now() )
         .build();

         miTransport.sendAsync(response);
      }
      catch( Exception exception )
      {
         throw Errors.miServiceReplyPublishFailed (
           "Failed to publish MI internal response",
           exception,
           fillAttrs( requestMessage, result ).toMap()
         );
      }
   }

   /** */
   private static Attrs fillAttrs( ReceivedMessage requestMessage, MiInternalResult result )
   {
      return
         Attrs.create()
           .put("queue", RESPONSE_QUEUE)
           .putIfNotNull( "request_id",        requestMessage == null ? null : requestMessage.getRequestId() )
           .putIfNotNull( "mi_correlation_id", requestMessage == null ? null : requestMessage.getMiCorrelationId() )
           .putIfNotNull( "response_code",     result == null ? null : result.responseCode() )
           .putIfNotNull( "response_category", result == null ? null : result.responseCategory() )
           .putIfNotNull( "response_info",     result == null ? null : result.responseInfo() )
           .putIfNotNull( "original_request_id", requestMessage == null ? null : requestMessage.getOriginalRequestId() );
   }
}