package ru.inversion.msmev.xxi.handler.mi_0007;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import ru.inversion.dataset.IParameters;
import ru.inversion.msmev.dto.XXLResponse;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.js.IMIScriptExecutor;
import ru.inversion.msmev.js.JSException;
import ru.inversion.msmev.transport.MiPublishReceipt;
import ru.inversion.msmev.transport.MiPublisher;
import ru.inversion.msmev.transport.PayloadDto;
import ru.inversion.msmev.transport.XxlMiEnvelope;
import ru.inversion.msmev.xxi.command.XxiCommandContext;
import ru.inversion.msmev.xxi.command.XxiCommandHandler;
import ru.inversion.msmev.xxi.repo.ReqRepository;
import ru.inversion.utils.ParametersValues;
import ru.inversion.utils.U;
import ru.inversion.utils.dco.Dco;
import ru.inversion.utils.dco.IDco;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class MI_0007_Handler extends XxiCommandHandler {

   /** */
   private static final int WSP_ID = 7;

   /**
    * Тип JS-скрипта для формирования request payload.
    */
   private static final int JS_TYPE_BUILD_REQUEST = 1;

   final private ReqRepository reqRepository;
   final private IMIScriptExecutor scriptExecutor;
   final private MI_0007_Repository itmRepository;

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
      boolean taken = false;

      try {

         reqRepository.take4Proc( context.reqId(), getClass().getSimpleName(), context.callUuid() );

         taken = true;

         XxlMiEnvelope envelope = prepareEnvelope(context);

         MiPublishReceipt receipt = miPublisher.publishAsync( envelope );

         reqRepository.toSent( context.reqId(), context.callUuid() );

         return XXLResponse.success()
                 .action    ( context.action() )
                 .resultCode( "SEND_PUBLISHED" )
                 .resultInfo( "Container published to MI" )
                 .parameters( context.parameters() )
                 .build();
      } catch (Throwable e) {
         throw handleSendException( context, e, taken, reqRepository );
      }
   }

   /** */
   private PayloadDto preparePayload( XxiCommandContext context )
   {
      try {

         final IParameters parameters = new ParametersValues();
         parameters.set("itemsList", itmRepository.getItemsList( context.reqId() ) );

         IDco dco = new Dco("request");

         scriptExecutor.execute( context.infId(), JS_TYPE_BUILD_REQUEST, dco, parameters );

         return new PayloadDto (
                 MediaType.APPLICATION_XML_VALUE, dco.asXmlBytes(StandardCharsets.UTF_8)
         );

      } catch( JSException e ) {
         throw Errors.payloadBuildFailed(
                 "JS payload build failed",
                 e,
                 U.toMap( "req_id", context.reqId(), "inf_id", context.infId(), "js_type", JS_TYPE_BUILD_REQUEST, "call_uuid", context.callUuid())
         );
      }
   }


   /** */
   private XxlMiEnvelope prepareEnvelope( XxiCommandContext context )
   {
      PayloadDto payloadDto;

      payloadDto = preparePayload( context );

      final XxlMiEnvelope.Builder builder = XxlMiEnvelope.xxiRequest(context);

      builder.source(new Consumer<XxlMiEnvelope.SourceBuilder>() {
                 @Override
                 public void accept(XxlMiEnvelope.SourceBuilder b) {
                    b.module("mi_0007");
                 }
              })
              .payload(new Consumer<XxlMiEnvelope.PayloadBuilder>() {
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