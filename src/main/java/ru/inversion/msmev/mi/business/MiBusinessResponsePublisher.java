package ru.inversion.msmev.mi.business;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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

   //public void publish(MiBusinessResponse response) {
      // publish to xxl.responses
   //}
}