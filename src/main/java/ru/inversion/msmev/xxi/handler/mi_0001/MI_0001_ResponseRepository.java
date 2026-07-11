package ru.inversion.msmev.xxi.handler.mi_0001;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import ru.inversion.mi.transport.model.MiAsyncItemResult;
import ru.inversion.msmev.mi.response.MiAsyncResponse;
import ru.inversion.msmev.mi.response.item.MiItemApplyResult;
import ru.inversion.msmev.mi.response.item.MiItemResultRepository;
import ru.inversion.msmev.xxi.repo.XxiRepositoryExecutor;
import ru.inversion.tc.TaskContext;

@Repository
@RequiredArgsConstructor
public class MI_0001_ResponseRepository implements MiItemResultRepository
{
   /** */
   private final XxiRepositoryExecutor db;

   @Override
   public MiItemApplyResult applyItem( MiAsyncResponse response, MiAsyncItemResult item, int itemIndex )
   {
      return db.execute( "MI_0001.applyItemResponse", response.itemParameters( item, itemIndex ),
        tc -> {
           MiItemApplyResult result = applyItemImpl(tc, response, item, itemIndex);
           tc.commit();
           return result;
        }
      );
   }

   /** */
   private MiItemApplyResult applyItemImpl( TaskContext tc, MiAsyncResponse response, MiAsyncItemResult item, int itemIndex )
   {
      return  null;
   }

}