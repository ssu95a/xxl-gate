package ru.inversion.edo.xxl.xxi.command.mi_0007;

import org.springframework.stereotype.Component;
import ru.inversion.dataset.IParameters;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.js.IMIScriptExecutor;
import ru.inversion.edo.xxl.js.JSException;
import ru.inversion.edo.xxl.transport.MiPublisher;
import ru.inversion.edo.xxl.transport.PayloadDto;
import ru.inversion.edo.xxl.transport.XxlMiEnvelope;
import ru.inversion.edo.xxl.xxi.command.XxiCommandContext;
import ru.inversion.edo.xxl.xxi.command.XxiCommandHandler;
import ru.inversion.edo.xxl.xxi.repo.ReqRepository;
import ru.inversion.utils.ParametersValues;
import ru.inversion.utils.dco.Dco;
import ru.inversion.utils.dco.IDco;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class MI_0007_Handler extends XxiCommandHandler {

   /** */
   private static final int WSP_ID = 7;

   /**
    * Тип JS-скрипта для формирования request payload.
    */
   private static final int JS_TYPE_BUILD_REQUEST = 1;

   final private IMIScriptExecutor  scriptExecutor;
   final private MI_0007_Repository itmRepository;

   /**
    * @param reqRepository
    * @param miPublisher
    */
   public MI_0007_Handler(ReqRepository reqRepository, MiPublisher miPublisher, IMIScriptExecutor scriptExecutor, MI_0007_Repository itmRepository) {
      super(reqRepository, miPublisher);
      this.scriptExecutor = scriptExecutor;
      this.itmRepository  = itmRepository;
   }

   @Override
   public int wspId() {
      return WSP_ID;
   }

   /** */
   private PayloadDto preparePayload( XxiCommandContext context )
   {
      List<Map<String, Object>> items = itmRepository.getItemsList( context.reqId() );

      IParameters parameters = new ParametersValues();
      parameters.set("itemsList", items);

      IDco dco = new Dco("request");

      try {
         scriptExecutor.execute( context.infId(), JS_TYPE_BUILD_REQUEST, dco, parameters );
      }
      catch (JSException exception) {
         throw Errors.payloadBuildFailed( "JS payload build failed", exception, context.parameters() );
      }

      final byte[] xml;

      try {
         xml = dco.asXmlBytes( StandardCharsets.UTF_8 );
      } catch (RuntimeException exception) {
         throw Errors.payloadBuildFailed( "XML payload serialization failed", exception, context.parameters() );
      }

      if( xml == null || xml.length == 0) {
         throw Errors.payloadBuildFailed( "JS produced empty XML payload", null, context.parameters() );
      }

      return PayloadDto.xml( xml );
   }

   /** */
   @Override
   protected XxlMiEnvelope prepareEnvelope( XxiCommandContext context )
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