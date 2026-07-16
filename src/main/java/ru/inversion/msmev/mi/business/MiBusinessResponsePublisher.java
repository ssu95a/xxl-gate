package ru.inversion.msmev.mi.business;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;

import static ru.inversion.msmev.mi.response.MiAsyncResponse.messageParameters;

@Slf4j
@Component
public class MiBusinessResponsePublisher
{
   /**
    * Сейчас MI не ожидает отдельный response от XXL.
    * Публикуем технически только в лог.
    */
   public void publish( ReceivedMessage requestMessage, MiBusinessResponse response )
   {
      if( response == null )
      {
         log.warn( "MI business response is null: request={}", messageParameters(requestMessage) );
         return;
      }

      if( "OK".equalsIgnoreCase(response.responseCategory()) )
      {
         log.info( "MI business technical response: responseCode={}, responseInfo={}, request={}", response.responseCode(), response.responseInfo(), messageParameters(requestMessage) );
         return;
      }

      log.warn (
              "MI business technical response failure: responseCode={}, responseInfo={}, responseCategory={}, attrs={}, request={}",
              response.responseCode(),
              response.responseInfo(),
              response.responseCategory(),
              response.attributes(),
              messageParameters(requestMessage)
      );
   }
}