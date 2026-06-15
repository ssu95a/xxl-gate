package ru.inversion.msmev.xxi.handler.mi_0001;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.msmev.dto.XXLResponse;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.mi.IMIEnvelope;
import ru.inversion.msmev.transport.MiPublishReceipt;
import ru.inversion.msmev.transport.MiPublisher;
import ru.inversion.msmev.transport.PayloadDto;
import ru.inversion.msmev.transport.MIEnvelope;
import ru.inversion.msmev.xxi.command.XxiCommandContext;
import ru.inversion.msmev.xxi.command.XxiCommandHandler;
import ru.inversion.msmev.xxi.repo.ReqRepository;
import ru.inversion.utils.U;

import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class MI_0001_Handler extends XxiCommandHandler {

   /** */
   private static final int WSP_ID = 1;


   final private ReqRepository reqRepository;
   final private MI_0001_Repository payloadRepository;

   private final MiPublisher miPublisher;

   @Override
   public int wspId() {
      return WSP_ID;
   }

/*
Порядок:
   take4Proc
   build payload
   miPublisher.publishAsync
   toSent
   return SEND_PUBLISHED
   если err, после toSent,
   то зовем toError
*/

   @Override
   public XXLResponse send( XxiCommandContext context )
   {
      int stage = 0;

      try {

         reqRepository.take4Proc(context.reqId(), getClass().getSimpleName(), context.callUuid());

         stage++;

         IMIEnvelope envelope = prepareEnvelope(context);

         stage++;

         MiPublishReceipt receipt = miPublisher.publishAsync(envelope);

         stage++;

         reqRepository.toSent(context.reqId(), context.callUuid());

         stage++;

         return XXLResponse.success()
                 .action(context.action())
                 .resultCode("SEND_PUBLISHED")
                 .resultInfo("Container published to MI")
                 .parameters(Errors.merge(
                         context.parameters(),
                         U.toMap("message_id", receipt.messageId(), "request_queue", receipt.requestQueue(),
                                 "response_queue", receipt.responseQueue(), "published_at", receipt.publishedAt())
                 ))
                 .build();

      } catch( Throwable e ) {
         throw handleSendException( context, e, stage > 0, reqRepository );
      }
   }

   /** */
   private IMIEnvelope prepareEnvelope(XxiCommandContext context )
   {
      PayloadDto payloadDto;

      try {
         payloadDto = payloadRepository.prepareItemList( context.reqId() );
      } catch( Exception e ) {
         throw Errors.payloadBuildFailed (
              "gzip payload build failed", e,
              U.toMap( "req_id", context.reqId(), "inf_id", context.infId(), "call_uuid", context.callUuid())
         );
      }

      MIEnvelope.Builder builder = MIEnvelope.builder(context);

      builder.source(new Consumer<MIEnvelope.SourceBuilder>() {
         @Override
         public void accept(MIEnvelope.SourceBuilder b) {
            b.module("mi_0001");
         }
      })
      .payload(new Consumer<MIEnvelope.PayloadBuilder>() {
         @Override
         public void accept(MIEnvelope.PayloadBuilder b) {

            b.contentType( payloadDto.mediaType())
             .data       ( payloadDto.data() )
             .dataSize   ( payloadDto.dataSize() );
         }
      });

      return builder.build();
   }
}