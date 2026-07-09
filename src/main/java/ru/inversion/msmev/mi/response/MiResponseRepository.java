package ru.inversion.msmev.mi.response;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.xxi.repo.XxiRepositoryExecutor;

import static ru.inversion.msmev.xxi.repo.ReqRepository.defXml;

@Repository
@RequiredArgsConstructor
public class MiResponseRepository {

   private final XxiRepositoryExecutor db;

   public void applyItemResult( MiAsyncResponse response )
   {
      db.executeVoid (
              "MiResponseRepository.applyItemResult",
              response.parameters(),
              tc -> {
                 IDataCall call = SQLCallBuilder.NEW(tc).url(defXml).name("apply_Response").build();
                 call.set(
                         "external_uuid",
                         response.messageId()
                 );

                 call.set(
                         "response_code",
                         response.responseCode()
                 );

                 call.set(
                         "response_info",
                         response.responseInfo()
                 );

                 int result =
                         call.execute().getReturnValue();

                 if (result != 0) {
                    tc.rollback();

                    throw Errors.miResponseApplyFailed(
                            "XXI rejected MI item response",
                            null,
                            response.parameters()
                    );
                 }

                 tc.commit();
              }
      );
   }
}