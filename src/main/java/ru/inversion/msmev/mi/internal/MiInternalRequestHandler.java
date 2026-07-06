package ru.inversion.msmev.mi.internal;

import java.util.Set;

public interface MiInternalRequestHandler
{
   Set<String> operations();

   MiInternalResult handle( MiInternalRequest request );
}