package ru.inversion.msmev.transport;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.IMITransport;
import ru.inversion.mi.transport.ITransportRequest;
import ru.inversion.mi.transport.TransportContainerRequest;
import ru.inversion.msmev.error.Errors;
import ru.inversion.utils.U;
import ru.inversion.utils.converter.TypeConverter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Collections;

/**
 * <h5>Default publisher request payload'ов XXL -> MI.</h5>
 * <p>
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

         builder.infNamespace( e.infNamespace() )
                .requestId   ( e.ids().externalRequestUuid().toString() )
                .miCorrelationId( e.ids().correlationId().toString() )
                .infId       ( e.ids().infId() )
                .xxlVersion  ( e.version() )
                .createdAt   ( e.createdAt().toString() )
                .sourceSystem( e.source().name() )
                .sourceSystem( e.version() )
                .mimeType    ( MediaType.parseMediaType( e.payload().contentType() ) );

         if( e.payload().data() instanceof Path )
             builder.payload( (Path)e.payload().data() );
         else
             builder.payload( TypeConverter.convert( e.payload().data(), byte[].class ) );

         ITransportRequest transportRequest = builder.build();

         messageBuilded = true;

         miTransport.sendAsync( transportRequest );

         return new MiPublishReceipt( e.ids().messageId(), null, null, e.ids().correlationId().toString(), OffsetDateTime.now() );

      } catch (Throwable th) {

         if( !messageBuilded  )
             throw Errors.payloadBuildFailed( "Failed build payload container", th, Collections.emptyMap() );

         throw Errors.miPublishFailed (
              "Failed to publish request to MI", th,
              U.toMap ( "req_id", e.ids().reqId(), "inf_id", e.ids().infId() )
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