package ru.inversion.msmev.mi.business;

/**
 * Handler конкретного бизнес-запроса MI -> XXI.
 *
 * Зона ответственности:
 * - выполняет бизнесовую регистрацию/обработку входящего запроса в XXI;
 * - возвращает MiBusinessResponse;
 * - не публикует ответ в xxl.responses сам.
 */
public interface MiBusinessRequestHandler {

   String requestType();

   //MiBusinessResponse handle(MiBusinessRequest request);
}