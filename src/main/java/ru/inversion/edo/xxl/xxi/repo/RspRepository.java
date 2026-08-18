package ru.inversion.edo.xxl.xxi.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.utils.U;

@Repository
@RequiredArgsConstructor
public class RspRepository {

   private final XxiRepositoryExecutor db;

   /** */
   public PRsp getResponse( long rspId )
   {
      return db.execute (
              "RspRepository.getResponse",
              U.toMap( "rsp_id", rspId ),
              tc -> {
                 PRsp rsp = new SQLDataSet<>(tc, PRsp.class).singleRow().wherePredicat( "rsp_id=" + rspId ) .execute() .getCurrentRow();
                 if(rsp == null)
                    throw Errors.requestNotFound(rspId);
                 return rsp;
              }
      );
   }

}
