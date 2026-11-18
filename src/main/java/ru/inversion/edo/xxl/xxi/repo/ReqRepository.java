package ru.inversion.edo.xxl.xxi.repo;

import org.springframework.stereotype.Repository;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.dataset.impl.XXIDsDao;
import ru.inversion.tc.TaskContext;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.util.Attrs;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.net.InetAddress;
import java.net.URL;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Repository
public class ReqRepository {

   final public static URL defXml = PReq.class.getResource( "plsql/def.xml" );

   private final XxiRepositoryExecutor db;

   public ReqRepository( XxiRepositoryExecutor db ) {
      this.db = db;
    }

   /**
    * Загружает MI_REQ через диалектно-зависимую проекцию.
    * Колонки с разными физическими именами приводятся к общим alias.
    */
   public PReq getRequest(long reqId)
   {
      return db.execute(
            "ReqRepository.getRequest",
            U.toMap("req_id", reqId),
            tc -> readRequest(tc, "PReqSql.byId", reqId, false));
   }

   /** */
   public PReq getRequest(UUID externalUuid)
   {
      if (externalUuid == null)
         throw new IllegalArgumentException("externalUuid is required");

      return db.execute(
            "ReqRepository.getRequestByExternalUuid",
            U.toMap("external_uuid", externalUuid),
            tc -> readRequest(tc, "PReqSql.byExternalUuid", externalUuid.toString(), true));
   }

   private static PReq readRequest(TaskContext tc, String queryName,
                                   Object key, boolean byUuid) throws SQLException
   {
      String sql = XXIDsDao.getNativeQuery(tc, PReqSql.class, queryName);
      if (sql == null || sql.isBlank())
         throw new SQLException("MI_REQ native SQL is missing: " + queryName);

      try (PreparedStatement ps = tc.getConnection().prepareStatement(sql))
      {
         if (key instanceof Long requestId)
            ps.setLong(1, requestId);
         else
            ps.setString(1, (String) key);

         try (ResultSet rs = ps.executeQuery())
         {
            if (!rs.next())
            {
               if (byUuid)
                  throw Errors.requestNotFound(UUID.fromString((String) key));
               throw Errors.requestNotFound((Long) key);
            }

            PReq request = mapRequest(rs);
            if (rs.next())
               throw new SQLException("MI_REQ returned multiple rows: " + queryName);

            return request;
         }
      }
   }

   /** Единый JDBC mapping для Oracle CHAR(36) и PostgreSQL UUID. */
   static PReq mapRequest(ResultSet rs) throws SQLException
   {
      PReq req = new PReq();
      long reqId = rs.getLong("req_id");
      if (rs.wasNull())
         throw new SQLException("MI_REQ.REQ_ID is NULL");
      req.setRequestId(reqId);

      int infId = rs.getInt("inf_id");
      if (rs.wasNull())
         throw new SQLException("MI_REQ.INF_ID is NULL, req_id=" + reqId);
      req.setInfId(infId);

      int status = rs.getInt("status_cd");
      if (rs.wasNull())
         throw new SQLException("MI_REQ.STATUS_CD is NULL, req_id=" + reqId);
      req.setStatus(status);

      int type = rs.getInt("itype");
      req.setType(rs.wasNull() ? null : type);

      req.setExternalUuid(readUuid(rs, "external_uuid", reqId));
      req.setCorrelationId(readUuid(rs, "correlation_id", reqId));
      req.setOriginalRequestUuid(readUuid(rs, "original_request_uuid", reqId));
      return req;
   }

   private static UUID readUuid(ResultSet rs, String column, long reqId)
         throws SQLException
   {
      String text = rs.getString(column);
      if (text == null)
         return null;
      try
      {
         return UUID.fromString(text.trim());
      }
      catch (IllegalArgumentException e)
      {
         throw new SQLException("MI_REQ invalid UUID in " + column + ": req_id=" + reqId, e);
      }
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

   /** */
   public void toError(long reqId, UUID callUuid)
   {
      executeXXICall( "to_Error", reqId, callUuid, null);
   }

   /** */
   public void applyRequestFailure ( UUID externalUuid, UUID messageUuid, String reasonCode, String errorCode, String errorInfo, String errorDetails, OffsetDateTime occurredAt  )
   {
      final Map<String, Object> parameters = U.toMap (
         "external_uuid", externalUuid,
         "message_uuid",  messageUuid,
         "reason_code",   reasonCode,
         "error_code",    errorCode,
         "error_info",    errorInfo,
         "error_details", errorDetails,
         "occurred_at",   occurredAt
      );

      db.executeVoid (
              "ReqRepository.applyRequestFailure",
              parameters,
              tc ->
              {
                  final IDataCall call
                     = SQLCallBuilder.NEW(tc).url(defXml).name("apply_Request_Failure")
                          .callBackParameters(ParametersByName.of(parameters))
                        .build();

                 int retCode = call.execute().getReturnValue();

                 String resInfo = call.get("res_info");

                 if( retCode == 0 ) {
                     tc.commit();
                     return;
                 }

                 tc.rollback();

                 //throw Errors.xxiCallFailed( callName, reqId, retValue, resInfo, callUuid );

                 throw Errors.miResponseApplyFailed(
                   "XXI apply_Request_Failure returned error: " + resInfo,
                   null,
                   Attrs.merge( parameters, U.toMap( "ret_code", retCode, "res_info", resInfo ) )
                 );
              }
      );
   }

   /** Найти inf_id исходного request по external_uuid. */
   public Integer findInfIdByExternalUuid(UUID externalUuid)
   {
      if( externalUuid == null )
         return null;

      return db.execute(
              "Req.findInfIdByExternalUuid",
              U.toMap("external_uuid", externalUuid),
              tc -> {
                 List<Integer> rows =
                     new SQLDataSet<>(tc, Integer.class)
                        .sql("select inf_id from xxi.mi_req where external_uuid='" + externalUuid + "'::uuid" ).rowMapper( (rs, rowNum) -> rs.getInt("inf_id"))
                           .queryAllRows()
                     .execute()
                        .getRows();

                 if( rows == null || rows.isEmpty() )
                     return null;

                 if( rows.size() > 1 )
                     throw Errors.internal( "More than one request found by external_uuid", null, U.toMap( "external_uuid", externalUuid, "row_count", rows.size() ) );

                 return rows.getFirst();
              }
      );
   }

}
