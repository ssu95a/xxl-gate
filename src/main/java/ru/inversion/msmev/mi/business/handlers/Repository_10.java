package ru.inversion.msmev.mi.business.handlers;

import org.springframework.stereotype.Repository;
import ru.inversion.msmev.mi.business.AbstractMiBusinessRepository;
import ru.inversion.msmev.xxi.repo.XxiRepositoryExecutor;

import java.net.URL;
import java.util.Set;

@Repository
public class Repository_10 extends AbstractMiBusinessRepository {

   private static final URL DEF_XML = Repository_10.class.getResource("plsql/def.xml");

   protected Repository_10(XxiRepositoryExecutor db ) {
      super(db);
   }

   public Set<Integer> infIds( )
   {
      return Set.of(10);
   }

   @Override
   protected URL defXml() {
      return DEF_XML;
   }

   @Override
   protected String operationName() {
      return "MI_0010.apply_Request";
   }

   @Override
   protected String callName()
   {
      return "MI_0010.apply_Request";
   }
}
