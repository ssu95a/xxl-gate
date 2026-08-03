package ru.inversion.edo.xxl.xxi.command.mi_0001;

import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.error.Errors;
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

   /** */
   final private MI_0001_Repository repo;

   /** */
   public MI_0001_Handler(ReqRepository reqRepository, MiPublisher miPublisher, MI_0001_Repository repo) {
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
         case ACTION_AUTO_PREPARE -> autoPrepare(request);
         default -> XxiDirectCommandHandler.super.handleDirect( command, request );
      };
   }

   private XXLResponse autoPrepare(XXLRequest request)
   {
      //Map<String, Object> parameters = request.parameters();

      long result = repo.submitAutoPrepare( );

      if(result == 0)
         throw Errors.internal(
           "MI_0001 submit_Auto_Prepare returned zero job id",
           null,
           U.toMap (
             "inf_id", request.getInfId(),
             "action", request.getAction(),
             "call_uuid", request.getCallUuid()
           )
         );

      boolean submitted = result > 0;
      long jobId = Math.abs(result);

      return XXLResponse.success()
              .action(request.getAction())
              .resultCode(
                      submitted
                              ? "AUTO_PREPARE_SUBMITTED"
                              : "AUTO_PREPARE_ALREADY_RUNNING"
              )
              .resultInfo(
                      submitted
                              ? "Auto prepare job submitted"
                              : "Auto prepare job is already submitted or running"
              )
              .parameter("job_id", jobId)
              .parameter("submitted", submitted)
              .parameter("inf_id", request.getInfId())
              .parameter("call_uuid", request.getCallUuid())
              .build();
   }
}