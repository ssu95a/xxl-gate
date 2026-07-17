package ru.inversion.msmev.mi.business;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.mi.transport.exception.MiTransportRetryException;
import ru.inversion.mi.transport.exception.MiTransportTerminalException;
import ru.inversion.mi.transport.listener.MITransportListener;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.mi.response.MiAsyncResponse;
import ru.inversion.msmev.util.XxlLog;

import java.util.Map;
import java.util.concurrent.TimeUnit;
/**
 * <h5>Listener бизнес-запросов S -> XXI (MI -> XXL).</h5>
 * <p>
 * Queue:
 * - input: mi-edo.requests
 * <p>
 * Зона ответственности:
 * - получает бизнес-запрос от MI;
 * - вызывает MiBusinessRequestDispatcher;
 * - завершает listener успешно только если запрос обработан;
 * - при retryable ошибке бросает transport retry exception;
 * - при terminal ошибке бросает transport terminal exception.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MiBusinessRequestListener
{
   private final MiBusinessRequestDispatcher dispatcher;

   @MITransportListener(queue = "${mi-edo.requests:mi-edo.requests}")
   public void handleRequest( ReceivedMessage message )
   {
      try( XxlLog.Scope ignored = XxlLog.module( XxlLog.Module.BUSINESS ) )
      {
         long startedAt = System.nanoTime();

         log.info( "MI business request received: {}", MiAsyncResponse.messageParameters(message) );

         MiBusinessResponse response = dispatcher.dispatch(message);

         if( response == null )
         {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            log.error(
                    "MI business request terminal: response is null, elapsedMs={}, request={}",
                    elapsedMs,
                    MiAsyncResponse.messageParameters(message)
            );

            throw new MiTransportTerminalException( Errors.ResultCode.XXL_INTERNAL_ERROR, "MI business response is null" );
         }

         long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

         Map<String, Object> responseInfo = response.dump();

         responseInfo.put("elapsed_ms", elapsedMs);

         if( "OK".equalsIgnoreCase(response.responseCategory()) )
         {
            log.info( "MI business request processed: {}", responseInfo );
            return;
         }

         if( shouldRetry(response) )
         {
            log.warn( "MI business request retry: {}", responseInfo );
            throw new MiTransportRetryException( response.responseCode(), response.responseInfo() );
         }

         log.warn( "MI business request terminal: {}", responseInfo );

         throw new MiTransportTerminalException( response.responseCode(), response.responseInfo() );
      }
   }

   private boolean shouldRetry( MiBusinessResponse response )
   {
      return switch( response.responseCode() )
      {
         case Errors.ResultCode.DB_ERROR,
              Errors.ResultCode.TECHNICAL_BREAK -> true;

         default -> false;
      };
   }
}