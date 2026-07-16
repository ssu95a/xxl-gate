package ru.inversion.msmev.mi.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.mi.transport.listener.MITransportListener;
import ru.inversion.msmev.error.XXLException;
import ru.inversion.msmev.util.XxlLog;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static ru.inversion.msmev.mi.response.MiAsyncResponse.messageParameters;

@Component
@RequiredArgsConstructor
@Slf4j
public final class MiInternalRequestListener
{
   private final MiInternalRequestParser parser;
   private final MiInternalRequestDispatcher dispatcher;
   private final MiInternalResponseSender responseSender;

   @MITransportListener(queue = "${mi-edo.xxl.queries.request:mi-edo.xxl.queries.request}")
   public void handleRequest( ReceivedMessage message )
   {
      try( XxlLog.Scope ignored = XxlLog.module(XxlLog.Module.INTERNAL) )
      {
         long startedAt = System.nanoTime();

         Map<String, Object> messageInfo = messageParameters(message);

         log.info("MI internal response received: {}", messageInfo);

         MiInternalResult result;

         try {

            MiInternalRequest request = parser.parse(message);
            result = dispatcher.dispatch(request);

         } catch (XXLException exception) {
            result = MiInternalResult.error(exception.getResultCode(), exception.getMessage(), exception.getAttributes());
         }
         catch (Exception exception) {
            log.error (
              "MI internal request processing failed: failureClass={}, message={}",
              exception.getClass().getName(), exception.getMessage(), exception
            );

            result = MiInternalResult.error("XXL_INTERNAL_ERROR", "Internal XXL query processing error");
         }

         long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

         Map<String, Object> resultInfo = result.dump();

         resultInfo.put("elapsed_ms", elapsedMs);

         if( "OK".equalsIgnoreCase(result.responseCategory()) )
              log.info("MI internal response processed: {}", resultInfo);
         else
              log.warn("MI internal response processed failure: {}", resultInfo);

         /*
          * Listener завершается только после успешной
          * отправки ответа.
          */
         responseSender.send( message, result );
      }
   }
}