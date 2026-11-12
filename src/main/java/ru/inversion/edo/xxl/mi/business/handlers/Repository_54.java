package ru.inversion.edo.xxl.mi.business.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.error.XXLException;
import ru.inversion.edo.xxl.mi.business.MiBusinessRepository;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequest;
import ru.inversion.edo.xxl.mi.business.MiBusinessResult;
import ru.inversion.edo.xxl.util.JsonMaps;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Pair;
import ru.inversion.utils.S;
import ru.inversion.utils.U;
import ru.inversion.utils.dco.Dco;
import ru.inversion.utils.io.RawBAOS;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
@Repository
public class Repository_54 implements MiBusinessRepository {

   private static final URL DEF_XML = Repository_54.class.getResource("plsql/def.xml");

   private static final String g_call_Name = "MI_request_Sup.apply_Request";

   private final XxiRepositoryExecutor db;

   /** */
   public Repository_54(XxiRepositoryExecutor db )
   {
      this.db = db;
   }

   @Override
   public Set<Integer> infIds() {
      return Set.of(54);
   }

   /** */
   private Map<String,Object> prepareParameters( MiBusinessRequest request )
   {
      final Map<String,Object> p = new LinkedHashMap<>();

      p.put( "message_uuid",   request.messageId() );
      p.put( "original_request_uuid",
                               request.requestId() );
      p.put( "correlation_id", U.nvl( request.correlationId(), UUID.randomUUID() ) );
      p.put( "request_time",   request.createdAt() );

      try {
         p.put( "payload_text",request.payload().openStream() );
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }

      return p;
   }


   /** */
   @Override
   public MiBusinessResult apply(MiBusinessRequest request)
   {
      final Map<String, Object> parameters = prepareParameters(request);

      UUID originalRequestUuid = (UUID) parameters.get("original_request_uuid");


      try {
         long itmId = db.execute( g_call_Name, parameters,tc -> applyRequest(tc, parameters ) );
         return MiBusinessResult.success( originalRequestUuid, itmId );
      }
      finally {

      }
   }


   /** */
   private long applyRequest( TaskContext tc, Map<String, Object> parameters ) throws Exception
   {
      Integer retVal   = null;
      String  retInf   = null;

      try {

            try( IDataCall call = SQLCallBuilder.NEW(tc).url(DEF_XML)
                    .name(g_call_Name).callBackParameters( ParametersByName.of(parameters) ).build().execute()
            )
            {
               retVal = call.getReturnValue();
               retInf = call.get("ret_info" );

               if( retVal == null || retVal != 0 )
                   throw Errors.xxiCallFailed( g_call_Name, 0L, U.nvl( retVal, -1), retInf, null );

               if( call.get("itm_id") == null )
                   throw Errors.xxiCallFailed( g_call_Name, 0L, retVal, "out parameter 'itm_id' is null", null );

               tc.commit();

               return call.get("itm_id");
            }
      }
      catch( Exception e ) {
         tc.rollback();
         throw e;
      }
   }
}
