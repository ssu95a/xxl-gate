package ru.inversion.msmev.transport;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.IMITransport;
import ru.inversion.mi.transport.ITransportRequest;
import ru.inversion.mi.transport.TransportContainerRequest;
import ru.inversion.msmev.error.Errors;
import ru.inversion.utils.U;
import ru.inversion.utils.converter.IConverter;
import ru.inversion.utils.converter.TypeConverter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
   public MiPublishReceipt publishAsync( XxlMiEnvelope e )
   {
      boolean messageBuilded = false;

      try {

         final TransportContainerRequest.Builder builder = TransportContainerRequest.builder();

         builder.requestId( e.ids().externalRequestUuid().toString() )
            .miCorrelationId( e.ids().correlationId().toString() )
               .urn( e.infNamespace() );

//         if( payload.isStream() )
//             builder.payload( e.asStream(), e.dataSize() );
//         else
//             builder.payload( e.asBytes() );

         if( e.payload().data() instanceof Path )
            builder.payload( Files.readAllBytes((Path)e.payload().data()) );
         else
            builder.payload( TypeConverter.convert( e.payload().data(), byte[].class ) );

         ITransportRequest miTransportMessage = builder.build();

         messageBuilded = true;

         miTransport.sendAsync( miTransportMessage );

         return new MiPublishReceipt( e.ids().messageId(), null, null, e.ids().correlationId().toString(), OffsetDateTime.now() );

      } catch (Throwable th) {

         if( !messageBuilded  )
             throw Errors.payloadBuildFailed( "Failed build payload container", th, Collections.emptyMap() );

         throw Errors.miPublishFailed (
              "Failed to publish request to MI", th,
              U.toMap (
                  "req_id", e.ids().reqId(),
                  "inf_id", e.ids().infId()
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