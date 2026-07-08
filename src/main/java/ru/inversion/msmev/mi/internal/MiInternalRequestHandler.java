package ru.inversion.msmev.mi.internal;

import java.util.Set;

/** */
public interface MiInternalRequestHandler
{
   /** */
   Set<String> queryTypes();

   /** */
   MiInternalResult handle( MiInternalRequest request );
}