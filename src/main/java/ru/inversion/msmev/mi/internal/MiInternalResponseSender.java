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

import java.time.OffsetDateTime;


@Component
@RequiredArgsConstructor
public final class MiInternalResponseSender
{
   public static final String RESPONSE_QUEUE = "mi-edo.xxl.queries.request-sync";

   private final IMITransport miTransport;
   private final ObjectMapper objectMapper;

   public void send (
      ReceivedMessage requestMessage,
      MiInternalResult result
   )
   {
      try
      {
         byte[] payload =
                 objectMapper.writeValueAsBytes(
                         result.data()
                 );

         ITransportRequest response =
                 TransportContainerRequest.builder()
                         .queueName(RESPONSE_QUEUE)

                         .originalRequestId(
                                 requestMessage.getRequestId()
                         )

                         .responseKind(
                                 MiAsyncResponseKind.ITEM_RESULT
                         )

                         .responseCode(
                                 result.responseCode()
                         )

                         .responseCategory(
                                 result.responseCategory()
                         )

                         .responseInfo(
                                 result.responseInfo()
                         )

                         .payload(payload)
                         .mimeType(MediaType.APPLICATION_JSON)

                         .sourceSystem("XXL")
                         .sourceVersion("1.0.0")
                         .createdAt(OffsetDateTime.now())

                         .build();

         miTransport.sendAsync(response);
      }
      catch( Exception exception )
      {
         throw new IllegalStateException(
                 "Failed to send MI query response"
                         + ", originalRequestId="
                         + requestMessage.getRequestId(),
                 exception
         );
      }
   }
}