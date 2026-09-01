package ru.inversion.edo.xxl.mi.business.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.error.XXLException;
import ru.inversion.edo.xxl.mi.business.MiBusinessPayload;
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
import ru.inversion.utils.dco.IDco;
import ru.inversion.utils.io.RawBAOS;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
   private void preparePayload( MiBusinessRequest request, Map<String, Object> parameters )
   {
      try
      {
         final Pair<String,String> descriptor = parseDescriptor(request);

         final Path tempZip = Files.createTempFile("mi_0025", ".zip");

         try
         {
            copyPayloadToZip( request.payload(), tempZip );

            try( ZipFile zip = new ZipFile(tempZip.toFile()) )
            {
               parameters.put( "xml_text",    readXml(zip, descriptor.first ) );
               parameters.put( "attach_file", readAttachment(zip, descriptor.second ) );
            }
         }
         finally
         {
            Files.deleteIfExists(tempZip);
         }
      }
      catch (XXLException e) {
         throw e;
      }
      catch (Exception e) {
         throw Errors.miBusinessPayloadBadFormat( "Error on parse MI business payload", e, request.dump() );
      }
   }

   /** */
   private Pair<String,String> parseDescriptor( MiBusinessRequest request )
   {
      Object value = request.headers().get("businessPayload");

      if(!(value instanceof String json) || S.isNullOrEmpty(json) )
         throw Errors.miBusinessPayloadBadFormat( "MI business header 'businessPayload' is empty", request.dump() );

      final Map<String, Object> descriptor;

      try
      {
         descriptor = JsonMaps.jsonToMap(json);
      }
      catch (Exception e) {
         throw Errors.miBusinessPayloadBadFormat( "MI business header 'businessPayload' is invalid JSON", e, request.dump());
      }

      String requestXml = (String) descriptor.get("requestXml");

      if( S.isNullOrEmpty(requestXml) )
          throw Errors.miBusinessPayloadBadFormat( "MI business descriptor field 'requestXml' is empty", request.dump() );

      String attachment = (String) descriptor.get("attachment");
      if( S.isNullOrEmpty(attachment) )
          throw Errors.miBusinessPayloadBadFormat( "MI business descriptor field 'attachment' is empty", request.dump() );

      return Pair.makePair( requestXml, attachment );
   }


   /** */
   private void copyPayloadToZip( MiBusinessPayload payload, Path tempZip ) throws Exception
   {
      try (InputStream in = payload.openStream())
      {
         Files.copy( in, tempZip, StandardCopyOption.REPLACE_EXISTING );
      }
   }

   /** */
   private ZipEntry getZipEntry(ZipFile zip, String entryName )
   {
      ZipEntry entry = zip.getEntry(entryName);

      if( entry == null )
          throw new IllegalArgumentException( "ZIP entry '" + entryName + "' not found" );

      if( entry.isDirectory() )
         throw new IllegalArgumentException( "ZIP entry '" + entryName + "' is a directory" );

      return entry;
   }

   /** */
   private String readXml( ZipFile zip, String entryName )
   {
      ZipEntry entry = getZipEntry(zip, entryName);

      try(InputStream in = zip.getInputStream(entry))
      {
         return Dco.parseXml(in).asXml();
      }
      catch (Exception e)
      {
         throw new IllegalArgumentException(
                 "Error parsing XML entry '" + entryName + "'",
                 e
         );
      }
   }

   /** */
   private InputStream readAttachment( ZipFile zip, String entryName )
   {
      ZipEntry entry = getZipEntry(zip, entryName);

      try (InputStream in = zip.getInputStream(entry))
      {
         RawBAOS baos = new RawBAOS();
         baos.write(in);

         return baos.inputStream();
      }
      catch (Exception e)
      {
         throw new IllegalArgumentException(
                 "Error reading attachment entry '" + entryName + "'",
                 e
         );
      }
   }

   /** */
   private long applyRequest( TaskContext tc, MiBusinessRequest request,  Map<String, Object> parameters ) throws Exception
   {
      Integer retVal   = null;
      String  retInf   = null;

      final InputStream attachData = (InputStream) parameters.get("attach_file");

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
      catch( Exception e ) {
         tc.rollback();
         throw e;
      }
      finally {

         if( attachData != null )
             attachData.close();

         parameters.remove("attach_file");
      }
   }
}
