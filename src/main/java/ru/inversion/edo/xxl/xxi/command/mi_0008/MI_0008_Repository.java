package ru.inversion.edo.xxl.xxi.command.mi_0008;

import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Repository;
import ru.inversion.dataset.IRowMapper;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.db.rs.RSUtils;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.transport.PayloadDto;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.edo.xxl.xxi.repo.PReq;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Holder;
import ru.inversion.utils.U;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.zip.GZIPOutputStream;

@Repository
@RequiredArgsConstructor
public class MI_0008_Repository {

   private final XxiRepositoryExecutor db;

   /**
    * <h6>Формирует GZIP/CSV payload из данных XXI.</h6>
    */
   public PayloadDto prepareData(PReq req )
   {
      return db.execute( "MI_0008.prepareData", U.toMap( "req_id", req ), tc -> buildPayload( tc, req ) );
   }

   /** */
   private PayloadDto buildPayload ( TaskContext tc, PReq req )
   {
      Path csvPath = null;
      boolean completed = false;

      try {

         csvPath = createTempFile( req.getRequestId() );

         int counter = writePayload( tc, req, csvPath );

         if( counter == 0 )
             throw Errors.emptyPayloadContainer( req.getRequestId(), U.toMap("req_id", req) );

         completed = true;

         return new PayloadDto( "application/gzip", csvPath, -1 );
      }
      finally {
         /*
          * Удаляем файл при любой незавершённой операции
          */
         if (!completed)
         {
            if( csvPath != null && Files.exists(csvPath))
               try {
                  Files.deleteIfExists(csvPath);
               } catch (IOException ignored)
               { }
         }
      }//end finally
   }


   /** Создаем временный файл */
   private static Path createTempFile( long reqId )
   {
      try {
         return Files.createTempFile( "xxl_0008_" + reqId + "_", ".gz" );
      } catch( IOException exception ) {
         throw Errors.payloadBuildFailed( "Не удалось создать временный файл payload", exception, U.toMap("req_id", reqId) );
      }
   }


   /** Пишем payload в файл*/
   private int writePayload( TaskContext tc, PReq req, Path csvPath )
   {

      try (
         GZIPOutputStream  out = new GZIPOutputStream( Files.newOutputStream(csvPath) );
         Writer         writer = new OutputStreamWriter( out, StandardCharsets.UTF_8 )
      )
      {
         Holder<CSVFormat>  csvFmtHolder = new Holder<>();
         Iterator<Object[]> iterator = createIterator( tc, req.getRequestId(), csvFmtHolder );

         try( CSVPrinter csvPrinter = new CSVPrinter( writer, csvFmtHolder.get() ) )
         {

            int counter = 0;

            while( true )
            {
               Object[] row;

               try {

                  if( !iterator.hasNext())
                      break;

                  row = iterator.next();

               } catch (Exception exception) {
                  throw Errors.dbError( "Ошибка чтения данных v_mi_0008_dat", exception, U.toMap("req_id", req) );
               }

               try {
                  csvPrinter.printRecord(row);
               } catch (IOException exception) {
                  throw Errors.payloadBuildFailed( "Ошибка записи CSV/GZIP payload", exception, U.toMap( "req_id", req, "row_number", counter + 1 ) );
               }

               counter++;
            }

            return counter;

         }

      } catch( IOException exception ) {
         throw Errors.payloadBuildFailed(
           "Ошибка создания GZIP payload",
           exception,
           U.toMap( "req_id", req, "temp_file", csvPath.toString() )
         );
      }
   }

   /** */
   private Iterator<Object[]> createIterator(TaskContext tc, long reqId, Holder<CSVFormat> csvFmtHolder ){

      try {

         final PreparedStatement ps =
                 tc.getConnection().prepareStatement("select * from v_mi_0008_dat where req_id = ?");

         ps.closeOnCompletion();
         ps.setLong( 1, reqId );

         final ResultSet rs  = ps.executeQuery();
         final int columnCount = rs.getMetaData().getColumnCount();

         csvFmtHolder.set( CSVFormat.Builder.create().setHeader(rs).setDelimiter(',').build() );

         return RSUtils.createIterator(rs, new IRowMapper<Object[]>() {
            @Override
            public Object[] mapRow( ResultSet rs, int rowNum) throws SQLException {

               final Object[] row = new Object[columnCount];

               for ( int i = 0, column = 1; i < columnCount; i++ )
               {
                  row[i] = rs.getObject(column++);
               }

               return row;
            }
         });

      } catch( Exception exception ) {
         throw Errors.dbError( "Ошибка выполнения запроса v_mi_0008_dat", exception, U.toMap("req_id", reqId) );
      }
   }

}