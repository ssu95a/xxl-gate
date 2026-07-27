package ru.inversion.msmev.mi.business.handlers;

import org.springframework.stereotype.Repository;
import ru.inversion.msmev.mi.business.MiBusinessRepository;
import ru.inversion.msmev.mi.business.MiBusinessRequest;
import ru.inversion.msmev.mi.business.MiBusinessResponse;
import ru.inversion.msmev.xxi.repo.XxiRepositoryExecutor;
import ru.inversion.tc.TaskContext;

import java.util.Map;
import java.util.Set;

@Repository
public class Repository_23 implements MiBusinessRepository {

   private final XxiRepositoryExecutor db;

   public Repository_23( XxiRepositoryExecutor db )
   {
      this.db = db;
   }

   @Override
   public Set<Integer> infIds() {
      return Set.of(23);
   }

   @Override
   public MiBusinessResponse apply( MiBusinessRequest request ) {
      return null;
   }

   /** */
   private MiBusinessResponse applyRequest( TaskContext tc, Map<String, Object> parameters )
   {
      throw new UnsupportedOperationException("applyRequest");
   }

}
