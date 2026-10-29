package ru.inversion.edo.xxl.mi.business.handlers;

import org.springframework.stereotype.Repository;
import ru.inversion.edo.xxl.mi.business.AbstractMiBusinessRepository;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;

import java.net.URL;
import java.util.Set;

@Repository
public class Repository_81 extends AbstractMiBusinessRepository {

   private static final URL DEF_XML = Repository_81.class.getResource("plsql/def.xml");

   protected Repository_81(XxiRepositoryExecutor db ) {
      super(db);
   }

   public Set<Integer> infIds( )
   {
      return Set.of(81);
   }

   @Override
   protected URL defXml() {
      return DEF_XML;
   }

   @Override
   protected String operationName() {
      return "MI_0081.apply_Request";
   }

   @Override
   protected String callName()
   {
      return "MI_0081.apply_Request";
   }
}
