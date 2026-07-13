package ru.inversion.msmev.mi.business;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.util.Attrs;

/**
 * <h5>Publisher ответа на бизнес-запрос MI -> XXI.</h5>
 *
 * Очередь:
 * - xxl.responses
 *
 * Зона ответственности:
 * - сериализует MiBusinessResponse;
 * - публикует сообщение в xxl.responses;
 * - при ошибке публикации бросает Errors.
 */
@Component
@RequiredArgsConstructor
public class MiBusinessResponsePublisher {

   public void publish ( ReceivedMessage requestMessage, MiBusinessResponse response )
   {
      throw Errors.miTransportResponseFailed(
           "MI business response publisher is not implemented yet",
           null,
           Attrs.create()
               .putIfNotNull( "original_request_id", response == null ? null : response.originalRequestId() )
               .putIfNotNull( "response_code", response == null ? null : response.responseCode() )
          .toMap()
      );
   }
}