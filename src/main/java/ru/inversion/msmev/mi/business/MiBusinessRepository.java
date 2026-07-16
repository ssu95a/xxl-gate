package ru.inversion.msmev.mi.business;

import java.util.Set;

/** */
public interface MiBusinessRepository {

   /** */
   default Set<Integer> infIds( )
   {
      return Set.of();
   }

   /** */
   MiBusinessResponse apply( MiBusinessRequest request );
}
