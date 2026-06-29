package ru.inversion.msmev.mi.response.item;

import ru.inversion.mi.transport.model.MiAsyncItemResult;
import ru.inversion.msmev.mi.response.MiAsyncResponse;

public interface MiItemResultRepository {

   /** */
   String infNamespace( );

   /** */
   MiItemApplyResult applyItem( MiAsyncResponse response, MiAsyncItemResult item, int itemIndex );
}