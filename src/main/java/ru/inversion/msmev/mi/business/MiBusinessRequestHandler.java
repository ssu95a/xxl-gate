package ru.inversion.msmev.mi.business;

import java.util.Set;

/**
 * <h5>Handler конкретного бизнес-запроса MI -> XXI.</h5>
 * <p>
 * Зона ответственности:
 * - выполняет бизнесовую регистрацию/обработку входящего запроса в XXI;
 * - возвращает MiBusinessResponse;
 * - не публикует ответ в xxl.responses сам.
 */
public interface MiBusinessRequestHandler
{
   Set<String> requestTypes();

   MiBusinessResponse handle(MiBusinessRequest request);
}