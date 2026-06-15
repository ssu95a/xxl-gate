package ru.inversion.msmev.mi.internal;

/**
 * Handler технического запроса MI -> XXL.
 *
 * Зона ответственности:
 * - реализует один технический serviceCode;
 * - может обращаться к XXI repository/API;
 * - возвращает MiInternalResponse;
 * - не занимается transport-level reply.
 */
public interface MiInternalServiceHandler {

   String serviceCode();

   //MiInternalResponse handle(MiInternalRequest request);
}