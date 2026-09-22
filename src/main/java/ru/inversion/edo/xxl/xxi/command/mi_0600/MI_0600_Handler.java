package ru.inversion.edo.xxl.xxi.command.mi_0600;

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

@Component
public class MI_0600_Handler extends XxiCommandHandler implements XxiDirectCommandHandler
{
   private static final int WSP_ID = 600;

   private final MI_0600_Repository repository;

   /** */
   public MI_0600_Handler( ReqRepository reqRepository, MiPublisher miPublisher, MI_0600_Repository repository )
   {
      super(reqRepository, miPublisher);
      this.repository = repository;
   }


   /** */
   @Override
   public int wspId()
   {
      return WSP_ID;
   }


   /** */
   @Override
   protected XxlMiEnvelope prepareEnvelope(XxiCommandContext context)
   {
      PayloadDto payload = repository.preparePayload(context.reqId());

      return XxlMiEnvelope.xxiRequest(context)
              .source (b ->b.module("mi_0600") )
              .payload(b -> b.contentType(payload.mediaType()).data(payload.data()).dataSize(payload.dataSize())).build();
   }

   /** */
   @Override
   public Set<XxiCommandKey> commands() {
      return Set.of( new XxiCommandKey(601, "SCHEDULE_CHANGE_NOTIFICATION") );
   }

   /** */
   @Override
   public XXLResponse handleDirect(XxiCommandKey command, XXLRequest request) {
      return XxiDirectCommandHandler.super.handleDirect(command, request);
   }
}