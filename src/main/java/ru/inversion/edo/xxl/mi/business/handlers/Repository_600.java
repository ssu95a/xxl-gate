package ru.inversion.edo.xxl.mi.business.handlers;

import org.springframework.stereotype.Repository;
import ru.inversion.edo.xxl.mi.business.AbstractMiBusinessRepository;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;

import java.net.URL;
import java.util.Set;

@Repository
public class Repository_600 extends AbstractMiBusinessRepository {

   private static final URL DEF_XML = Repository_600.class.getResource("plsql/def.xml");

   protected Repository_600(XxiRepositoryExecutor db ) {
      super(db);
   }

   public Set<Integer> infIds( )
   {
      return Set.of(602,604);
   }

   @Override
   protected URL defXml() {
      return DEF_XML;
   }

   @Override
   protected String operationName() {
      return "MI_0600.apply_Request";
   }

   @Override
   protected String callName()
   {
      return "MI_0600.apply_Request";
   }
}
