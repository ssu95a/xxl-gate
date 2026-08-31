package ru.inversion.edo.xxl.mi.business.handlers;

import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.stereotype.Repository;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.DataSetException;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.error.XXLException;
import ru.inversion.edo.xxl.mi.business.MiBusinessPayload;
import ru.inversion.edo.xxl.mi.business.MiBusinessRepository;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequest;
import ru.inversion.edo.xxl.mi.business.MiBusinessResult;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;
import ru.inversion.utils.U;
import ru.inversion.utils.dco.Dco;
import ru.inversion.utils.dco.IDco;
import ru.inversion.utils.io.RawBAOS;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

@Slf4j
@Repository
public class Repository_25 implements MiBusinessRepository {

   private static final URL DEF_XML = Repository_25.class.getResource("plsql/def.xml");

   private final XxiRepositoryExecutor db;

   public Repository_25( XxiRepositoryExecutor db )
   {
      this.db = db;
   }

   @Override
   public Set<Integer> infIds() {
      return Set.of(25);
   }

   /** */
   private Map<String,Object> prepareParameters( MiBusinessRequest request )
   {
      final Map<String,Object> p = new LinkedHashMap<>();

      p.put( "message_uuid",   request.messageId() );
      p.put( "original_request_uuid",
                               request.requestId() );
      p.put( "correlation_id", request.correlationId() );
      p.put( "request_time",   request.createdAt() );

      preparePayload( request, p );

      return p;
   }

   @Override
   public MiBusinessResult apply( MiBusinessRequest request ) {

      Map<String, Object> parameters = prepareParameters(request);

      UUID originalRequestUuid = (UUID) parameters.get("original_request_uuid");

      long itmId = db.execute (
           "MI_0025.proc",
           parameters,
           tc -> {
              return applyRequest(tc, request, parameters);
           }
      );
      return MiBusinessResult.success( originalRequestUuid, itmId );
   }

   /** */
   private void preparePayload( MiBusinessRequest request, Map<String,Object> parameters  ) {
      // "businessPayload" : "{\"recipient\":\"organization_1\",\"requestXml\":\"request.xml\",\"requestXmlSignature\":\" request.xml.sig\",\"attachment\":\"scan.pdf\"}"

      try {

         MiBusinessPayload  payload = request.payload();
         Map<String,Object> headers = request.headers();

         String requestXml = null;
         String attachment = null;

         try {

            IDco dco = Dco.parseJson("businessPayload", (String) headers.get("businessPayload"));

            if( dco.hasItem("requestXml") )
                requestXml = dco.e("requestXml").value();

            if( dco.hasItem("attachment") )
                attachment = dco.e("attachment").value();

         } catch( Exception e ) {
            throw new IllegalStateException("Error on parse header JSON", e);
         }

         if( S.isNullOrEmpty(requestXml) )
             throw new IllegalStateException("No 'requestXml' parameter in businessPayload");

         if( S.isNullOrEmpty(attachment) )
             throw new IllegalStateException("No 'attachment' parameter in businessPayload");

         String requestData = null;
         InputStream attachData = null;

         Path tempZip = Files.createTempFile("mi_0025", ".zip");

         try {

            try( InputStream in = payload.openStream() )
            {
               Files.copy(in, tempZip, StandardCopyOption.REPLACE_EXISTING);
            }

            try( ZipFile zip = new ZipFile(tempZip.toFile()) )
            {
               ZipEntry entry = zip.getEntry(requestXml);

               if( entry == null )
                   throw new IllegalStateException("No zip entry '" + requestXml + "' in zip stream");

               try {
                  final IDco dco = Dco.parseXml(zip.getInputStream(entry));
                  requestData = dco.asXml();
               } catch (Exception e) {
                  throw new IllegalStateException("Error parse XML from '" + requestXml + "'");
               }

               entry = zip.getEntry(attachment);

               if( entry == null )
                   throw new IllegalStateException("No zip entry '" + attachment + "' in zip stream");

               try {

                  final RawBAOS baos = new RawBAOS();
                  baos.write( zip.getInputStream(entry) );

                  attachData = baos.inputStream();
               } catch (Exception e) {
                  throw new IllegalStateException("Error read data from '" + attachment + "'");
               }

               parameters.put( "xml_text",    requestData);
               parameters.put( "attach_file", attachData );
            }

         } catch( Exception e ) {
               throw new RuntimeException(e);
         } finally {
            Files.deleteIfExists(tempZip);
         }
      } catch ( Exception e ) {
         throw Errors.miBusinessPayloadBadFormat( "Error on parse MI business payload", e, request.dump() );
      }
   }


   /** */
   private long applyRequest( TaskContext tc, MiBusinessRequest request,  Map<String, Object> parameters ) throws Exception
   {
      Integer retVal   = null;
      String  retInf   = null;

      try {

             try( IDataCall call = SQLCallBuilder.NEW(tc).url(DEF_XML).name("MI_0025.create_item").callBackParameters( ParametersByName.of(parameters) ).build().execute() )
             {

                retVal = call.getReturnValue();
                retInf = call.get("ret_info");

                if( retVal == null || retVal != 0 )
                    throw Errors.xxiCallFailed( "MI_0025.create_item", 0L, U.nvl( retVal, -1), retInf, null );

                if( call.get("itm_id") == null )
                    throw Errors.xxiCallFailed( "MI_0025.create_item", 0L, retVal, "out parameter 'itm_id' is null", null );

                tc.commit();

                return call.get("itm_id");
             }
      }
      catch( Exception e )
      {
         tc.rollback();
         throw e;
      }
   }
}
