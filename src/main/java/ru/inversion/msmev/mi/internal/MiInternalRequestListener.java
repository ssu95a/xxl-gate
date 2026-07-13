package ru.inversion.msmev.mi.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.mi.transport.listener.MITransportListener;
import ru.inversion.msmev.error.XXLException;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static ru.inversion.msmev.mi.response.MiAsyncResponse.messageParameters;

@Component
@RequiredArgsConstructor
@Slf4j
public final class MiInternalRequestListener
{
   public static final String REQUEST_QUEUE = "mi-edo.xxl.queries.request";

   private final MiInternalRequestParser parser;
   private final MiInternalRequestDispatcher dispatcher;
   private final MiInternalResponseSender responseSender;
/*
      Map<String, Object> messageInfo = messageParameters( message );

      log.info( "MI async response received: {}", messageInfo );

      ProcessResult result = dispatcher.dispatch( message );

      long elapsedMs = TimeUnit.NANOSECONDS.toMillis( System.nanoTime() - startedAt );

      if( result.success() ) {
         log.info( "MI async response processed: resultCode={}, resultInfo={}, elapsedMs={}, params={}", result.resultCode(), result.resultInfo(), elapsedMs, result.parameters() );
         return;
      }

      if( result.shouldRetry() ) {
         log.warn( "MI async response retry: resultCode={}, resultInfo={}, elapsedMs={}, params={}", result.resultCode(), result.resultInfo(), elapsedMs, result.parameters() );
         throw new MiTransportRetryException(result.resultCode(), result.resultInfo());
      }

      log.error( "MI async response terminal: resultCode={}, resultInfo={}, elapsedMs={}, params={}", result.resultCode(), result.resultInfo(), elapsedMs, result.parameters() );

      throw new MiTransportTerminalException( result.resultCode(), result.resultInfo() );

 */
   @MITransportListener(queue = REQUEST_QUEUE)
   public void handleRequest( ReceivedMessage message )
   {
      long startedAt = System.nanoTime();

      Map<String, Object> messageInfo = messageParameters( message );

      log.info( "MI async response received: {}", messageInfo );

      MiInternalResult result;

      try
      {
         MiInternalRequest request = parser.parse(message);
         result = dispatcher.dispatch(request);

      }
      catch( XXLException exception ) {
         result = MiInternalResult.error( exception.getResultCode(), exception.getMessage(), exception.getAttributes() );
      }
      catch( Exception exception ) {
         result = MiInternalResult.error( "XXL_INTERNAL_ERROR", "Internal XXL query processing error" );
      }

      long elapsedMs = TimeUnit.NANOSECONDS.toMillis( System.nanoTime() - startedAt );

      if( "OK".equalsIgnoreCase( result.responseCategory() ) )
         log.info( "MI internal response processed: responseCode={}, responseInfo={}, elapsedMs={}, data={}", result.responseCode(), result.responseInfo(), elapsedMs, result.data() );
      else
         log.warn( "MI internal response processed failure: responseCode={}, responseInfo={}, elapsedMs={}, data={}", result.responseCode(), result.responseInfo(), elapsedMs, result.data() );

      /*
       * Listener завершается только после успешной
       * отправки ответа.
       */
      responseSender.send(message, result);
   }
}