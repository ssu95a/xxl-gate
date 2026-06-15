package ru.inversion.msmev.transport;



import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.IMITransport;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.mi.IMIEnvelope;
import ru.inversion.utils.U;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.UUID;

/**
 * Default publisher request payload'ов XXL -> MI.
 *
 * Зона ответственности:
 * - построить transport envelope;
 * - вызвать MiTransportClient;
 * - преобразовать transport exception в Errors.miPublishFailed(...).
 */
@Component
@RequiredArgsConstructor
public class DefaultMiPublisher implements MiPublisher {

   final private IMITransport miTransport;

   @Override
   public MiPublishReceipt publishAsync( IMIEnvelope envelope )
   {
      boolean messageReady = false;

      final UUID messageId = UUID.randomUUID();

      try {

         /*
         final TransportContainerRequest.Builder builder = TransportContainerRequest.builder();

         builder.requestId( context.req().getExternalUuid().toString() )
            .miCorrelationId( context.req().getCorrelationId().toString() )
                  .urn( context.inf().getNamespace() );

         if( payload.isStream() )
             builder.payload( payload.asStream(), payload.dataSize() );
         else
             builder.payload( payload.asBytes() );

         ITransportRequest miTransportMessage = builder.build();

         messageReady = true;
         */


         miTransport.sendAsync( miTransportMessage );

         return new MiPublishReceipt( messageId, null, null, envelope.ids().correlationId().toString(), OffsetDateTime.now() );

      } catch (Throwable e) {

         if( !messageReady  )
             throw Errors.payloadBuildFailed( "Failed build payload container", e, Collections.emptyMap() );

         throw Errors.miPublishFailed (
              "Failed to publish request to MI",
              e,
              U.toMap (
                  "req_id", envelope.ids().reqId(),
                  "inf_id", envelope.ids().infId()
                  //"request_queue", requestQueue, "response_queue", responseQueue, "ttl_ms", ttlMs
              )
         );
      }
   }

/*
   private String resolveRequestQueue( XxiCommandContext context )
   {
      String queue = context.inf().getRequestQueue();

      if( queue == null || queue.isBlank() )
      {
         throw Errors.config(
                 "MI request queue is not configured",
                 Errors.mapOf(
                         "req_id", context.requestId(),
                         "inf_id", context.infId(),
                         "wsp_id", context.wspId(),
                         "call_uuid", context.callUuid()
                 )
         );
      }

      return queue;
   }

   private String resolveResponseQueue(XxiCommandContext context) {
      String queue = context.inf().getResponseQueue();

      if (queue == null || queue.isBlank()) {
         return "mi-edo.responses";
      }

      return queue;
   }
*/
}