package ru.inversion.edo.xxl.xxi.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.utils.U;

import java.net.URL;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RspRepository {

   public static final URL defXml = PRsp.class.getResource("plsql/def.xml");

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

   /** */
   public String getPayload( long rspId )
   {
      return db.execute (
              "RspRepository.getResponse",
              U.toMap( "rsp_id", rspId ),
              tc -> {
                 String s = new SQLDataSet<>(tc, String.class).singleRow().sql( "select payload::text from mi_rsp where rsp_id =" + rspId ).execute().getCurrentRow();
                 if(s == null)
                    throw Errors.requestNotFound(rspId);
                 return s;
              }
      );
   }

   /** */
   public void toSent( long rspId, UUID callUuid )
   {
      db.executeVoid (
        "RspRepository.toSent",
        U.toMap( "rsp_id", rspId, "call_uuid", callUuid ),
        tc -> {

           IDataCall call = SQLCallBuilder.NEW(tc).url(defXml).name("rsp_to_Sent").build();
           call.set("rsp_id", rspId);
           int retCode = call.execute().getReturnValue();
           String resInfo = call.get("res_info");

           if( retCode == 0 )
           {
              tc.commit();
              return;
           }

           tc.rollback();

           throw Errors.xxiResponseCallFailed( "MI_Response_Api.to_Sent", rspId, retCode, resInfo, callUuid );
        }
      );
   }
}
