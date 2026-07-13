package ru.inversion.msmev.mi.business;

import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;

/**
 * Parser бизнес-запросов S -> XXI.
 *
 * Зона ответственности:
 * - извлекает тип бизнес-запроса;
 * - извлекает correlation/message identifiers;
 * - разбирает payload;
 * - валидирует обязательные атрибуты;
 * - бросает Errors.miServiceBadFormat или отдельный business bad format.
 */
@Component
public class MiBusinessRequestParser {

   public MiBusinessRequest parse(ReceivedMessage message) {
      return null;
   }
}