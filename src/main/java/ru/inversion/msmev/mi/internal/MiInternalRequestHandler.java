package ru.inversion.msmev.mi.internal;

import java.util.Set;

/** */
public interface MiInternalRequestHandler
{
   /** */
   Set<String> queryTypes();

   /** */
   InternalResult handle(InternalRequest request );
}