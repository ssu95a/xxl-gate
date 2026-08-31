package ru.inversion.edo.xxl.mi.response.item.mi_0003;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import ru.inversion.edo.xxl.mi.response.item.AbstractMiItemResultRepository;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;

import java.net.URL;
import java.util.Set;

@Repository
public class MI_0003_ResponseRepository extends AbstractMiItemResultRepository
{
   private static final Set<Integer> INF_IDS = Set.of( 32, 34 );

   private static final URL DEF_XML = MI_0003_ResponseRepository.class.getResource("plsql/def.xml");

   /** */
   public MI_0003_ResponseRepository(XxiRepositoryExecutor db, ObjectMapper objectMapper )
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
      return "MI_0003.applyItemResponse";
   }
}