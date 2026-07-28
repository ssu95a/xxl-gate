package ru.inversion.msmev.mi.business.handlers;

import org.springframework.stereotype.Repository;
import ru.inversion.msmev.mi.business.AbstractMiBusinessRepository;
import ru.inversion.msmev.mi.business.MiBusinessRepository;
import ru.inversion.msmev.mi.business.MiBusinessRequest;
import ru.inversion.msmev.mi.business.MiBusinessResponse;
import ru.inversion.msmev.xxi.repo.XxiRepositoryExecutor;
import ru.inversion.tc.TaskContext;

import java.net.URL;
import java.util.Map;
import java.util.Set;

@Repository
public class Repository_23 extends AbstractMiBusinessRepository {

   private static final URL DEF_XML = Repository_10.class.getResource("plsql/def.xml");

   public Repository_23( XxiRepositoryExecutor db )
   {
      super(db);
   }

   @Override
   protected URL defXml() {
      return DEF_XML;
   }

   @Override
   protected String operationName() {
      return null;
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
      return null;
   }
}
