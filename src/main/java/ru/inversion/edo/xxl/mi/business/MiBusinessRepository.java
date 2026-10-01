package ru.inversion.edo.xxl.mi.business;

import java.util.Set;

/** */
public interface MiBusinessRepository {

   /** */
   default Set<Integer> infIds( )
   {
      return Set.of();
   }

   /** */
   MiBusinessResult apply(MiBusinessRequest request );
}
