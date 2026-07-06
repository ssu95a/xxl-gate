package ru.inversion.msmev.mi.internal;

public interface MiInternalRequestHandler {

   boolean supports(MiInternalRequest request);

   MiInternalResult handle(MiInternalRequest request);
}