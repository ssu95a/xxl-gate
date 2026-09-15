package ru.inversion.edo.xxl.xxi.command.mi_0600;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.transport.PayloadDto;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.U;
import ru.inversion.utils.converter.TypeConverter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Repository
@RequiredArgsConstructor
public class MI_0600_Repository
{
   public record CreateResult(long reqId, long itmId) {}


   private static final URL DEF_XML = MI_0600_Repository.class.getResource("plsql/def.xml");

   private static final String CREATE_CALL_NAME = "MI_0600.create_Request";

   private static final String PREPARE_PAYLOAD_OPERATION = "MI_0600.preparePayload";

   private static final String ZIP_MEDIA_TYPE = "application/zip";

   private static final String PAYLOAD_SQL ="select bzip_data from xxi.mi_0600 where req_id = ?";


   private final XxiRepositoryExecutor db;


   /**
    * Создает request + единственный MI_0600 item
    * и сохраняет ZIP в XXI.
    */
   public CreateResult createRequest( int infId, Path zipPath, List<String> fileNames )
   {
      if( zipPath == null || !Files.isRegularFile(zipPath) )
         throw new IllegalArgumentException( "ZIP file does not exist: " + zipPath );

      if( fileNames == null || fileNames.isEmpty() )
         throw new IllegalArgumentException( "File list is empty" );

      final long zipSize;

      try {
         zipSize = Files.size(zipPath);
      }
      catch( IOException e ) {
         throw Errors.payloadBuildFailed( "Не удалось определить размер ZIP", e, U.toMap( "zip_file", zipPath.toString() ) );
      }


      try( InputStream zipData = Files.newInputStream(zipPath) )
      {
         Map<String, Object> parameters = new LinkedHashMap<>();

         parameters.put( "inf_id"  , infId   );
         parameters.put( "zip_name", zipPath.getFileName().toString() );
         parameters.put( "zip_data", zipData );
         parameters.put( "zip_size", zipSize );
         parameters.put( "zip_files_count", fileNames.size() );
         parameters.put( "file_names",      fileNames.toArray( String[]::new) );
         parameters.put( "create_type",   0 );

         return db.execute( CREATE_CALL_NAME, parameters, tc -> callCreateRequest( tc, parameters ) );
      }
      catch( IOException e ) {
         throw Errors.payloadBuildFailed( "Не удалось открыть ZIP для сохранения в XXI", e, U.toMap( "zip_file", zipPath.toString(), "zip_size", zipSize ) );
      }
   }


   /**
    * Материализует сохраненный в XXI ZIP во временный файл.
    * <p>
    * При успешном возврате repository файл не удаляет:
    * дальнейший lifecycle принадлежит transport-слою.
    */
   public PayloadDto preparePayload(long reqId)
   {
      return db.execute( PREPARE_PAYLOAD_OPERATION, U.toMap( "param_keys", List.of("req_id"), "param_count", 1 ), tc -> buildPayload(tc, reqId));
   }


   /**
    * Вызов PG API создания request.
    */
   private CreateResult callCreateRequest( TaskContext tc, Map<String, Object> parameters ) throws Exception
   {
      try
      {
         try( IDataCall call = SQLCallBuilder.NEW(tc).url(DEF_XML).name(CREATE_CALL_NAME).callBackParameters( ParametersByName.of(parameters)).build().execute() )
         {
            Integer retCode = call.getReturnValue();
            String  retInfo = call.get("ret_info");
            Long    itmId   = call.get("itm_id"  );
            Long    reqId   = call.get("req_id"  );

            if( retCode == null || retCode != 0 )
               throw Errors.xxiCallFailed( CREATE_CALL_NAME, 0L, U.nvl(retCode, -1), retInfo, null );

            if( reqId == null )
                throw Errors.xxiCallFailed( CREATE_CALL_NAME, 0L, retCode, "out parameter 'req_id' is null", null );

            if( itmId == null )
                throw Errors.xxiCallFailed( CREATE_CALL_NAME, 0L, retCode, "out parameter 'itm_id' is null", null );

            tc.commit();

            return new CreateResult( reqId, itmId );
         }
      }
      catch( Exception e )
      {
         tc.rollback();
         throw e;
      }
   }


   /**
    * Создает временный ZIP и выгружает в него bytea.
    * <p>
    * При любой ошибке временный файл удаляется.
    */
   private PayloadDto buildPayload( TaskContext tc, long reqId )
   {
      Path zipPath = null;
      boolean completed = false;

      try
      {
         zipPath = createTempZipFileName(reqId);
         long zipSize = writePayload( tc, reqId, zipPath );

         completed = true;

         return new PayloadDto( ZIP_MEDIA_TYPE, zipPath, zipSize );
      }
      finally
      {
         /*
          * Если repository не смог вернуть готовый payload
          */
         if( !completed )
             deleteTempFile(zipPath);
      }
   }


   /**
    * Потоково копирует xxi.mi_0600.bzip_data во временный файл.
    */
   private long writePayload( TaskContext tc, long reqId, Path zipPath )
   {
      try( PreparedStatement ps = tc.getConnection().prepareStatement(PAYLOAD_SQL) )
      {
         ps.setLong( 1, reqId);

         try( ResultSet rs = ps.executeQuery() )
         {
            if( !rs.next() )
                throw Errors.payloadBuildFailed( "MI_0600 item не найден для request", null, U.toMap( "req_id", reqId ) );

            final long actualSize;

            try( InputStream input = rs.getBinaryStream("bzip_data") )
            {
               if( input == null )
                  throw Errors.payloadBuildFailed( "MI_0600 ZIP payload отсутствует", null, U.toMap( "req_id", reqId ));

               try
               {
                  actualSize = Files.copy( input, zipPath, StandardCopyOption.REPLACE_EXISTING );
               }
               catch( IOException e ) {
                  throw Errors.payloadBuildFailed( "Не удалось записать MI_0600 ZIP во временный файл", e, U.toMap( "req_id", reqId ) );
               }
            }

            if( actualSize == 0 )
               throw Errors.payloadBuildFailed( "MI_0600 ZIP payload пуст", null, U.toMap("req_id", reqId ) );

            /*
             * По нашему контракту request содержит ровно один MI_0600 item.
             * Не отправляем произвольную первую строку, если данные нарушены.
             */
            if( rs.next() )
                throw Errors.payloadBuildFailed( "Для MI_0600 request найдено более одного item", null, U.toMap( "req_id", reqId ) );

            return actualSize;
         }
      }
      catch( SQLException | IOException e ) {
         throw Errors.dbError( PREPARE_PAYLOAD_OPERATION, e, U.toMap( "req_id", reqId ) );
      }
   }


   /** */
   private Path createTempZipFileName( long reqId )
   {
      try
      {
         return Files.createTempFile( "xxl_0600_" + reqId + "_", ".zip" );
      }
      catch( IOException e ) {
         throw Errors.payloadBuildFailed( "Не удалось создать временный файл MI_0600 payload", e, U.toMap( "req_id", reqId ) );
      }
   }


   /** */
   private void deleteTempFile(Path path)
   {
      if( path == null )
          return;

      try {
         Files.deleteIfExists(path);
      }
      catch( IOException ignored )
      { }
   }


   /** */
   public Duration getScanDelay()
   {
      return db.execute("getScanDelay", Collections.emptyMap(), tc -> {

         Duration retValue = null;

         try( PreparedStatement ps = tc.getConnection().prepareStatement("select MI_prp.get_Wsp_Property(600,'SCAN_DELAY')::numeric") )
         {
            try( ResultSet rs = ps.executeQuery() )
            {
               if( rs.next() )
               {
                  Object o = rs.getObject(1);
                  if( o != null )
                      retValue = Duration.ofMinutes(TypeConverter.convert(o,Long.class));
               }
            }
         }

         return retValue;
      });
   }

   /** */
   public List<InfConfig> loadInfConfigs( )
   {
      return db.execute("loadInfConfigs", Collections.emptyMap(), tc1 -> new SQLDataSet<>(tc1,InfConfig.class).queryAllRows().execute().getRows());

   }

   private static final String SEND_PENDING_OPERATION = "MI_0600.sendPending";

   private static final String SEND_PENDING_SQL = "call MI_0600_Api.send_Pending()";

   /** */
   public void sendPending()
   {
      db.execute(
              SEND_PENDING_OPERATION,
              Collections.emptyMap(),
              tc -> callSendPending(tc)
      );
   }


   /** */
   private Void callSendPending(TaskContext tc) throws Exception
   {
      try
      {
         try( PreparedStatement ps = tc.getConnection().prepareStatement(SEND_PENDING_SQL) ) {
            ps.execute();
         }

         tc.commit();

         return null;
      }
      catch( Exception e )
      {
         tc.rollback();
         throw e;
      }
   }
}