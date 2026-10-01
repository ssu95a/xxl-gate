package ru.inversion.edo.xxl.mi.response.item;

import ru.inversion.mi.transport.async.model.MiAsyncItemResult;
import ru.inversion.edo.xxl.mi.response.MiAsyncResponse;

import java.util.Set;

public interface MiItemResultRepository {

   /** */
   default Set<Integer> infIds( )
   {
      return Set.of();
   }

   /** */
   MiItemApplyResult applyItem( MiAsyncResponse response, MiAsyncItemResult item, int itemIndex );
}