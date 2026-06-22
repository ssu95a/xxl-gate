package ru.inversion.msmev.xxi.repo;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.stereotype.Repository;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.DataSetException;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.msmev.Tags;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.error.XXLException;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.net.InetAddress;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;

@Repository
public class ReqRepository {

   final public static URL defXml = PReq.class.getResource( "plsql/def.xml" );

   private final XxiRepositoryExecutor db;

   public ReqRepository( XxiRepositoryExecutor db ) {
      this.db = db;
    }

   /** */
   public PReq getRequest(long reqId)
   {
       return db.execute (
         "ReqRepository.getRequest",
         U.toMap( "req_id", reqId ),
               tc -> {
                  PReq req = new SQLDataSet<>(tc, PReq.class).singleRow().wherePredicat( "req_id=" + reqId ) .execute() .getCurrentRow();

                  if (req == null)
                     throw Errors.requestNotFound(reqId);

                  return req;
               }
       );
   }

   /** */
   public PReq getRequest(UUID externalUuid)
   {
      return db.execute (
              "ReqRepository.getRequestByExternalUuid",
              U.toMap( "external_uuid", externalUuid ),
              tc -> {

                 PReq req = new SQLDataSet<>(tc, PReq.class).set( "external_uuid", externalUuid ).<SQLDataSet<PReq>>to().wherePredicat("external_uuid = :external_uuid").execute().getCurrentRow();

                 if( req == null )
                    throw Errors.requestNotFound( externalUuid );


                 return req;
              }
      );
   }


   private void executeXXICall(
           String callName,
           long reqId,
           UUID callUuid,
           Consumer<IDataCall> customizer
   )
   {
      db.executeVoid (
              "ReqRepository." + callName,
              U.toMap(
                      "call_name", callName,
                      "req_id", reqId,
                      "call_uuid", callUuid
              ),
              tc -> {
                 IDataCall call = SQLCallBuilder.NEW(tc) .url(defXml).name(callName).build();
                 call.set("req_id", reqId);

                 if( customizer != null )
                     customizer.accept(call);

                 int retValue = call.execute().getReturnValue();

                 if( retValue == 0 ) {
                    tc.commit();
                    return;
                 }

                 String resInfo = call.get("res_info");

                 tc.rollback();

                 throw Errors.xxiCallFailed( callName, reqId, retValue, resInfo, callUuid );
              }
      );
   }

   /** */
   public void take4Proc(long reqId, String handlerInfo, UUID callUuid) {

      String info = S.EMPTY_STRING;

      try {
         final String hostName = InetAddress.getLocalHost().getCanonicalHostName();
         info = ", из " + hostName;
      } catch (Throwable ignored) {
      }

      String finalInfo = info;

      executeXXICall( "take_For_Proc", reqId, callUuid,
              call -> call.set(
                      "info",
                      "Взят в обработку, MULTI-BUS " + handlerInfo + ", в " + LocalDateTime.now() + finalInfo)
      );
   }

   /** */
   public void toSent(long reqId, UUID callUuid)
   {
      executeXXICall( "to_Sent", reqId, callUuid, null);
   }

   public void toError(long reqId, UUID callUuid)
   {
      executeXXICall( "to_Error", reqId, callUuid, null);
   }
}
