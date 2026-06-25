package ru.inversion.msmev.xxi.handler.mi_0001;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import ru.inversion.msmev.mi.response.MiAsyncResponse;
import ru.inversion.msmev.mi.response.MiItemResultRepository;
import ru.inversion.msmev.xxi.repo.XxiRepositoryExecutor;
import ru.inversion.tc.TaskContext;

@Repository
@RequiredArgsConstructor
public class MI_0001_ResponseRepository
        implements MiItemResultRepository {

   private static final String INF_NAMESPACE =
           "mi_0001";

   private final XxiRepositoryExecutor db;

   @Override
   public String infNamespace()
   {
      return INF_NAMESPACE;
   }

   @Override
   public void apply(MiAsyncResponse response)
   {
      db.execute(
              "MI_0001.applyResponse",
              response.parameters(),
              tc -> {
                 applyContainer(
                         tc,
                         response
                 );

                 return null;
              }
      );
   }

   private void applyContainer(
           TaskContext tc,
           MiAsyncResponse response
   ) {
      // Конкретная логика MI_0001.
   }
}