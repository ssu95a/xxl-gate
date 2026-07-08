package ru.inversion.msmev.mi.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.mi.transport.listener.MITransportListener;
import ru.inversion.msmev.error.XXLException;

@Component
@RequiredArgsConstructor
public final class MiInternalRequestListener
{
   public static final String REQUEST_QUEUE = "mi-edo.xxl.queries.request";

   private final MiInternalRequestParser parser;
   private final MiInternalRequestDispatcher dispatcher;
   private final MiInternalResponseSender responseSender;

   @MITransportListener(queue = REQUEST_QUEUE)
   public void handleRequest( ReceivedMessage message )
   {
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

      /*
       * Listener завершается только после успешной
       * отправки ответа.
       *
       * Ошибка sendAsync должна выйти наружу,
       * чтобы входное сообщение не было ACK.
       */
      responseSender.send(message, result);
   }
}