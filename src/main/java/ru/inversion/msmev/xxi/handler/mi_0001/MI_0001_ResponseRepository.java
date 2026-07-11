package ru.inversion.msmev.xxi.handler.mi_0001;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import ru.inversion.msmev.xxi.handler.AbstractMiItemResultRepository;
import ru.inversion.msmev.xxi.repo.XxiRepositoryExecutor;

import java.net.URL;
import java.util.Set;

@Repository
public class MI_0001_ResponseRepository extends AbstractMiItemResultRepository
{
   private static final Set<Integer> INF_IDS = Set.of( 12, 13 );

   private static final URL DEF_XML = MI_0001_ResponseRepository.class.getResource("plsql/def.xml");

   /** */
   public MI_0001_ResponseRepository( XxiRepositoryExecutor db, ObjectMapper objectMapper )
   {
      super(db, objectMapper);
   }

   @Override
   public Set<Integer> infIds()
   {
      return INF_IDS;
   }

   @Override
   protected URL defXml()
   {
      return DEF_XML;
   }

   @Override
   protected String operationName()
   {
      return "MI_0001.applyItemResponse";
   }
}