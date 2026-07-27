package ru.inversion.msmev.mi.internal;

import java.util.Set;

/** */
public interface InternalRequestHandler
{
   /** */
   Set<String> queryTypes();

   /** */
   InternalResult handle(InternalRequest request );
}