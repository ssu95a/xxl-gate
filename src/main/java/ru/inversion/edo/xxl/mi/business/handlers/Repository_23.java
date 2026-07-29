package ru.inversion.edo.xxl.mi.business.handlers;

import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.stereotype.Repository;
import ru.inversion.datacall.CallException;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.DataSetException;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.edo.xxl.error.XXLException;
import ru.inversion.edo.xxl.error.XXLExceptionMapper;
import ru.inversion.edo.xxl.mi.business.*;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.edo.xxl.xxi.protocol.XXLResponse;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.io.InputStream;
import java.net.URL;
import java.util.*;

@Slf4j
@Repository
public class Repository_23 implements MiBusinessRepository {

   private static final URL DEF_XML = Repository_10.class.getResource("plsql/def.xml");

   private final XxiRepositoryExecutor db;

   public Repository_23( XxiRepositoryExecutor db )
   {
      this.db = db;
   }

   @Override
   public Set<Integer> infIds() {
      return Set.of(23);
   }

   private Map<String,Object> prepareParameters( MiBusinessRequest request )
   {
      Map<String,Object> p = new LinkedHashMap<>();

      p.put("message_uuid",          request.messageId() );
      p.put("original_request_uuid", request.requestId() );
      p.put("correlation_id",        request.correlationId());
      p.put("request_time",          request.createdAt() );

      return p;
   }

   @Override
   public MiBusinessResponse apply( MiBusinessRequest request ) {

      Map<String, Object> parameters = prepareParameters(request);

      UUID originalRequestUuid = (UUID) parameters.get("original_request_uuid");

      boolean ok =  db.<Boolean>execute (
           "MI_0023.proc",
           parameters,
           tc -> {
              boolean ret =  applyRequest(tc, request.payload(), parameters);
              if( ret )
                  tc.commit();
              else
                  tc.rollback();
              return ret;
           }
      );

      return ok ?
             MiBusinessResponse.ok( originalRequestUuid, null )
             :
             MiBusinessResponse.ok( originalRequestUuid, "PROCESS_ALREADY_RUNNING", "Закачка CSV файла идет в данный момент. Новая попытка будет прервана. Попробуйте позже." );
   }

   /** */
   private String getCopyCommand( TaskContext tc ) throws DataSetException {

      String command =
              new SQLDataSet<>(tc, String.class)
                  .sql("select mi_0023_api.get_copy_command()")
                      .rowMapper( (rs,n)->rs.getString(1) )
                         .singleRow()
                      .execute()
                          .getCurrentRow();

      if( S.isNullOrEmpty(command) )
          throw new NoSuchElementException("text command for PG command 'copy' is null or empty");

      return command;
   }

   /** */
   private void loadCsv( TaskContext tc, MiBusinessPayload payload) throws Exception
   {
      final String copyCommand = getCopyCommand(tc);

      final CopyManager cm = tc.getConnection ().unwrap (PGConnection.class).getCopyAPI();

      try( InputStream is = payload.openStream() ) {
           long l = cm.copyIn( copyCommand, is );
      }
   }

   /** */
   private boolean applyRequest( TaskContext tc, MiBusinessPayload payload, Map<String, Object> parameters ) throws Exception
   {
      int stage = 0;

      Integer retVal   = null;
      String  retInf   = null;
      String  callName = null;

      try {

         for( ++stage; stage < 5; stage++ )
         {
             if( stage == 3 )
             {
                callName = "loadCsv";
                loadCsv( tc, payload );
                continue;
             }

             try( IDataCall call =
                switch ( stage ) {
                   case 1 ->
                   SQLCallBuilder.NEW(tc).url(DEF_XML).name(callName = "MI_0023.before_Load")
                        .build()
                           .execute();
                   case 2 ->
                      SQLCallBuilder.NEW(tc).url(DEF_XML).name(callName = "MI_0023.apply_Request")
                            .callBackParameters( ParametersByName.of(parameters) )
                         .build()
                             .execute();
                   case 4 ->
                      SQLCallBuilder.NEW(tc).url(DEF_XML).name(callName = "MI_0023.after_Load")
                              .callBackParameters( ParametersByName.of(parameters) )
                              .build()
                              .execute();
                   default ->
                           null;
                }
             )
             {
                retVal = call.getReturnValue();
                retInf = call.get("ret_info");

                if( retVal != null && retVal == 0 )
                    continue;

                if( stage == 1 && U.nvl(retVal,-1) == 3 )
                {
                   // Если уже идет процесс загрузки CSV,
                   // то прерываем выполнение, но не считаем это ошибкой
                   return false;
                }

                throw Errors.xxiCallFailed( callName, 0L, U.nvl( retVal, -1), retInf, null );
             }
         }
         return true;
      }
      catch( Exception e )
      {
         tc.rollback();

         try {

            if( e instanceof XXLException )
                SQLCallBuilder.NEW(tc).url(DEF_XML).name("MI_0023.abort_Load2").build().execute();
            else
                SQLCallBuilder.NEW(tc).url(DEF_XML).name("MI_0023.abort_Load3").build().set("error_info", "error on Java layer: " + e.getMessage() ).execute();

         } catch( Exception suppressed ) {
            //
            log.warn("Error on call abort_Load", suppressed );
         }

         throw e;
      }
   }
}
