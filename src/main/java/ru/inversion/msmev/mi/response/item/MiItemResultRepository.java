package ru.inversion.msmev.mi.response.item;

import ru.inversion.mi.transport.model.MiAsyncItemResult;
import ru.inversion.msmev.mi.response.MiAsyncResponse;

import java.util.Set;

public interface MiItemResultRepository {

   /** */
   String infNamespace( );

   /** */
   default Set<Integer> infIds()
   {
      return Set.of();
   }

   /** */
   MiItemApplyResult applyItem( MiAsyncResponse response, MiAsyncItemResult item, int itemIndex );
}