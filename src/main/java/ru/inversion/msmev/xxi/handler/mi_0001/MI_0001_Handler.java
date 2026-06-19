package ru.inversion.msmev.xxi.handler.mi_0001;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.inversion.msmev.dto.XXLResponse;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.transport.MiPublishReceipt;
import ru.inversion.msmev.transport.MiPublisher;
import ru.inversion.msmev.transport.PayloadDto;
import ru.inversion.msmev.transport.XxlMiEnvelope;
import ru.inversion.msmev.xxi.command.XxiCommandContext;
import ru.inversion.msmev.xxi.command.XxiCommandHandler;
import ru.inversion.msmev.xxi.repo.ReqRepository;
import ru.inversion.utils.U;

import java.util.function.Consumer;

@Component
public class MI_0001_Handler extends XxiCommandHandler {

   /** */
   private static final int WSP_ID = 1;

   /** */
   final private MI_0001_Repository payloadRepository;

   /** */
   public MI_0001_Handler( ReqRepository reqRepository, MiPublisher miPublisher, MI_0001_Repository payloadRepository ) {
      super( reqRepository, miPublisher );
      this.payloadRepository = payloadRepository;
   }

   @Override
   public int wspId() {
      return WSP_ID;
   }

   /** */
   @Override
   protected XxlMiEnvelope prepareEnvelope( XxiCommandContext context )
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

      XxlMiEnvelope.Builder builder = XxlMiEnvelope.xxiRequest(context);

      builder.source( new Consumer<XxlMiEnvelope.SourceBuilder>() {
         @Override
         public void accept( XxlMiEnvelope.SourceBuilder b ) {
            b.module("mi_0001");
         }
      })
      .payload( new Consumer<XxlMiEnvelope.PayloadBuilder>() {
         @Override
         public void accept(XxlMiEnvelope.PayloadBuilder b) {
            b.contentType( payloadDto.mediaType())
             .data       ( payloadDto.data() )
             .dataSize   ( payloadDto.dataSize() );
         }
      });

      return builder.build();
   }
}