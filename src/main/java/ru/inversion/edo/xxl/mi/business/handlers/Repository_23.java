package ru.inversion.edo.xxl.mi.business.handlers;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.stereotype.Repository;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.DataSetException;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.edo.xxl.mi.business.AbstractMiBusinessRepository;
import ru.inversion.edo.xxl.mi.business.MiBusinessPayload;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequest;
import ru.inversion.edo.xxl.mi.business.MiBusinessResponse;
import ru.inversion.edo.xxl.slf.error.Errors;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;

import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Repository
public class Repository_23 extends AbstractMiBusinessRepository {

   private static final URL DEF_XML = Repository_10.class.getResource("plsql/def.xml");

   public Repository_23( XxiRepositoryExecutor db )
   {
      super(db);
   }

   @Override
   protected URL defXml() {
      return DEF_XML;
   }

   @Override
   protected String operationName() {
      return "MI_0023.csv_Load";
   }

   @Override
   public Set<Integer> infIds() {
      return Set.of(23);
   }

   @Override
   public MiBusinessResponse apply( MiBusinessRequest request ) {

      Map<String, Object> parameters = prepareParameters(request);

      return db.execute (
              operationName(),
              parameters,
              tc -> {
                 MiBusinessResponse result = applyRequest(tc, request.payload(), parameters);
                 tc.commit();
                 return result;
              }
      );
   }

   /** */
   private String getCopyCommand( TaskContext tc ) throws DataSetException {

      String command =
              (String) new SQLDataSet<>(tc, String.class)
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
   private MiBusinessResponse applyRequest( TaskContext tc, MiBusinessPayload payload, Map<String, Object> parameters )
   {
      int stage = 0;

      Integer retVal = null;
      String  retInf = null;

      UUID originalRequestUuid = (UUID) parameters.get("original_request_uuid");

      try {

         for( ++stage; stage < 5; stage++ ) {

             if( stage == 3 ) {
                loadCsv( tc, payload );
                continue;
             }

             try( IDataCall call =
                switch ( stage ) {
                   case 1 ->
                      SQLCallBuilder.NEW(tc).url(DEF_XML).name("MI_0023.before_Load")
                        .build()
                           .execute();
                   case 2 ->
                      SQLCallBuilder.NEW(tc).url(DEF_XML).name("MI_0023.apply_Request")
                            .callBackParameters( ParametersByName.of(parameters) )
                         .build()
                             .execute();
                   case 4 ->
                      SQLCallBuilder.NEW(tc).url(DEF_XML).name("MI_0023.after_load")
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

                if( retVal == null || retVal != 0)
                   throw new IllegalStateException("error 'retVal' value from call");
             }
         }
         return MiBusinessResponse.ok( originalRequestUuid, null );
      }
      catch ( Exception e)
      {
         return MiBusinessResponse.error (
           originalRequestUuid,
           retVal == null ? Errors.ResultCode.XXI_CALL_FAILED : Integer.toString(retVal),
           retInf, parameters
         );
      }
   }
}
