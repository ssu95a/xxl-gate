package ru.inversion.edo.xxl.xxi.command.mi_0008;

import org.springframework.stereotype.Component;
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

import java.util.Set;
import java.util.function.Consumer;

@Component
public class MI_0008_Handler extends XxiCommandHandler implements XxiDirectCommandHandler {

   /** */
   private static final int WSP_ID = 8;

   /** */
   final private MI_0008_Repository repo;

   /** */
   public MI_0008_Handler(ReqRepository reqRepository, MiPublisher miPublisher, MI_0008_Repository repo) {
      super(reqRepository, miPublisher);
      this.repo = repo;
   }

   @Override
   public int wspId() {
      return WSP_ID;
   }

   /** */
   @Override
   protected XxlMiEnvelope prepareEnvelope(XxiCommandContext context) {

      PayloadDto payloadDto = repo.prepareData(context.reqId());

      XxlMiEnvelope.Builder builder = XxlMiEnvelope.xxiRequest(context);

      builder.source(new Consumer<XxlMiEnvelope.SourceBuilder>() {
                 @Override
                 public void accept(XxlMiEnvelope.SourceBuilder b) {
                    b.module("mi_0008");
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

   @Override
   public Set<XxiCommandKey> commands() {
      return Set.of();
   }

   @Override
   public XXLResponse handleDirect(XxiCommandKey command, XXLRequest request) {
      return XxiDirectCommandHandler.super.handleDirect(command, request);
   }
}