package ru.inversion.edo.xxl.xxi.command.mi_0600;

import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.error.XXLException;
import ru.inversion.edo.xxl.transport.MiPublisher;
import ru.inversion.edo.xxl.transport.PayloadDto;
import ru.inversion.edo.xxl.transport.XxlMiEnvelope;
import ru.inversion.edo.xxl.xxi.command.XxiCommandContext;
import ru.inversion.edo.xxl.xxi.command.XxiCommandContextFactory;
import ru.inversion.edo.xxl.xxi.command.XxiCommandHandler;
import ru.inversion.edo.xxl.xxi.command.XxiCommandKey;
import ru.inversion.edo.xxl.xxi.command.XxiCommandStatus;
import ru.inversion.edo.xxl.xxi.command.XxiDirectCommandHandler;
import ru.inversion.edo.xxl.xxi.protocol.XXLRequest;
import ru.inversion.edo.xxl.xxi.protocol.XXLResponse;
import ru.inversion.edo.xxl.xxi.repo.ReqRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class MI_0606_Handler extends XxiCommandHandler implements XxiDirectCommandHandler
{
   private static final int WSP_ID = 600;
   private static final int INF_ID = 606;

   private static final XxiCommandKey SEND_DOCUMENT = new XxiCommandKey(INF_ID, "send_document");

   private final MI_0606_Repository repository;
   private final XxiCommandContextFactory contextFactory;

   public MI_0606_Handler(ReqRepository reqRepository, MiPublisher miPublisher,
                          MI_0606_Repository repository, XxiCommandContextFactory contextFactory)
   {
      super(reqRepository, miPublisher);
      this.repository = repository;
      this.contextFactory = contextFactory;
   }

   /** */
   @Override
   public int wspId()
   {
      return WSP_ID;
   }

   @Override
   public Integer infId() { return INF_ID; }

   @Override
   public Set<XxiCommandKey> commands()
   {
      return Set.of(SEND_DOCUMENT);
   }

   @Override
   public XXLResponse handleDirect(XxiCommandKey command, XXLRequest request)
   {
      long outfileId = switch (request.parameters().get("outfile_id")) {
         case Integer id -> id.longValue();
         case Long id -> id;
         case null, default -> 0L;
      };

      if (outfileId <= 0)
         throw Errors.contract("send_document requires a positive integer outfile_id",
               Map.of("inf_id", INF_ID, "call_uuid", request.getCallUuid()));

      MI_0606_Repository.Registration registration = repository.createRequest(outfileId);
      long reqId = registration.reqId();

      try {
         XxiCommandContext context = contextFactory.createForRegisteredRequest(request, reqId);

         if (registration.alreadyRegistered() || context.status() != XxiCommandStatus.STATUS_NEW) {
            return XXLResponse.success()
                  .action(command.action())
                  .resultCode("ALREADY_REGISTERED")
                  .resultInfo("Документ уже зарегистрирован в MI")
                  .parameters(context.parameters())
                  .build();
         }

         XXLResponse result = send(context);
         return XXLResponse.success()
               .action(result.getAction())
               .resultCode(result.getResultCode())
               .resultInfo(result.getResultInfo())
               .parameters(result.getParameters())
               .parameter("status_cd", XxiCommandStatus.STATUS_SENT.value())
               .build();
      } catch (XXLException failure) {
         Map<String, Object> attributes = new LinkedHashMap<>(failure.getAttributes());
         attributes.put("req_id", reqId);
         attributes.put("outfile_id", outfileId);
         if (Errors.ResultCode.MI_PUBLISH_FAILED.equals(failure.getResultCode())
               || Errors.ResultCode.MI_PUBLISHED_STATUS_UPDATE_FAILED.equals(failure.getResultCode())) {
            attributes.put("delivery_outcome", "UNKNOWN");
            attributes.put("retry_safe", "0");
         }
         throw new XXLException(failure.getNamespace(), failure.getResultCode(), failure.getMessage(),
               failure, failure.getLogPolicy(), attributes);
      } catch (RuntimeException failure) {
         throw Errors.internal("Не удалось отправить зарегистрированный документ 606", failure,
               Map.of("req_id", reqId, "outfile_id", outfileId));
      }
   }

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
