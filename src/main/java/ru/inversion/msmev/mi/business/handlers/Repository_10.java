package ru.inversion.msmev.mi.business.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import ru.inversion.msmev.mi.business.AbstractMiBusinessRepository;
import ru.inversion.msmev.xxi.repo.XxiRepositoryExecutor;

import java.net.URL;
import java.util.Set;

@Repository
public class Repository_10 extends AbstractMiBusinessRepository {

   protected Repository_10(XxiRepositoryExecutor db, ObjectMapper objectMapper) {
      super(db, objectMapper);
   }

   public Set<Integer> infIds( )
   {
      return Set.of(10);
   }

   @Override
   protected URL defXml() {
      return null;
   }

   @Override
   protected String operationName() {
      return "";
   }
}
