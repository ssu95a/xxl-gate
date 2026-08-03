package ru.inversion.edo.xxl.xxi.command.mi_0001;

import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.mi.business.handlers.Repository_10;
import ru.inversion.edo.xxl.transport.MiPublisher;
import ru.inversion.edo.xxl.transport.PayloadDto;
import ru.inversion.edo.xxl.transport.XxlMiEnvelope;
import ru.inversion.edo.xxl.xxi.command.XxiCommandContext;
import ru.inversion.edo.xxl.xxi.command.XxiCommandHandler;
import ru.inversion.edo.xxl.xxi.command.XxiCommandKey;
import ru.inversion.edo.xxl.xxi.command.XxiDirectCommandHandler;
import ru.inversion.edo.xxl.xxi.protocol.XXLRequest;
import ru.inversion.edo.xxl.xxi.protocol.XXLResponse;
import ru.inversion.edo.xxl.xxi.repo.ReqRepository;
import ru.inversion.utils.U;

import java.util.Set;
import java.util.function.Consumer;

@Component
public class MI_0001_Handler extends XxiCommandHandler implements XxiDirectCommandHandler {

   /** */
   private static final int WSP_ID = 1;

   /**
    */
   final private MI_0001_Repository repo;
   private final Repository_10 repository_10;

   /**
    *
    */
   public MI_0001_Handler(ReqRepository reqRepository, MiPublisher miPublisher, MI_0001_Repository repo, Repository_10 repository_10) {
      super(reqRepository, miPublisher);
      this.repo = repo;
      this.repository_10 = repository_10;
   }

   @Override
   public int wspId() {
      return WSP_ID;
   }

   /**
    *
    */
   @Override
   protected XxlMiEnvelope prepareEnvelope(XxiCommandContext context) {
      PayloadDto payloadDto = repo.prepareItemList(context.reqId());

      XxlMiEnvelope.Builder builder = XxlMiEnvelope.xxiRequest(context);

      builder.source(new Consumer<XxlMiEnvelope.SourceBuilder>() {
                 @Override
                 public void accept(XxlMiEnvelope.SourceBuilder b) {
                    b.module("mi_0001");
                 }
              })
              .payload(new Consumer<XxlMiEnvelope.PayloadBuilder>() {
                 @Override
                 public void accept(XxlMiEnvelope.PayloadBuilder b) {
                    b.contentType(payloadDto.mediaType())
                            .data(payloadDto.data())
                            .dataSize(payloadDto.dataSize());
                 }
              });

      return builder.build();
   }

   // Command handler zone
   private static final String ACTION_AUTO_PREPARE  = "auto_prepare";

   private static final Set<XxiCommandKey> COMMANDS = Set.of(new XxiCommandKey( 13, ACTION_AUTO_PREPARE) );

   @Override
   public Set<XxiCommandKey> commands() {
      return COMMANDS;
   }

   /** */
   @Override
   public XXLResponse handleDirect( XxiCommandKey command, XXLRequest request )
   {
      return switch( command.action() )
      {
//         case ACTION_AUTO_PREPARE ->
//                  repo.submitAutoPrepare();

         default -> XxiDirectCommandHandler.super.handleDirect( command, request );
      };
   }
}