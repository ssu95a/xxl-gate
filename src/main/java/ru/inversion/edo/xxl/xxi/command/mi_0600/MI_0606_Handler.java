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
import ru.inversion.mi.transport.sync.MiTransport;
import ru.inversion.mi.transport.sync.contract.ScheduleChangeNotification;

import java.util.Set;

@Component
public class MI_0606_Handler extends XxiCommandHandler
{
   private static final int WSP_ID = 600;
   private static final int INF_ID = 606;

   private final MI_0606_Repository repository;
   private final MiTransport transport;

   /** */
   public MI_0606_Handler(ReqRepository reqRepository, MiPublisher miPublisher, MI_0606_Repository repository, MiTransport transport )
   {
      super(reqRepository, miPublisher);
      this.repository = repository;
      this.transport  = transport;
   }

   /** */
   @Override
   public int wspId()
   {
      return WSP_ID;
   }

   @Override
   public Integer infId() { return INF_ID; }

   /** */
   @Override
   protected XxlMiEnvelope prepareEnvelope(XxiCommandContext context)
   {
      PayloadDto payload = repository.prepareSendDocument(context.reqId());

      return XxlMiEnvelope.xxiRequest(context)
              .source(b -> b.module("mi_0606"))
              .payload(b -> b
                      .contentType(payload.mediaType())
                      .data(payload.data())
                      .dataSize(payload.dataSize()))
              .build();
   }

}