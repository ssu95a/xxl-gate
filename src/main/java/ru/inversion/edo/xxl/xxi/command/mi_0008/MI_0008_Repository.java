package ru.inversion.edo.xxl.xxi.command.mi_0008;

import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Repository;
import ru.inversion.dataset.IRowMapper;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.transport.PayloadDto;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.U;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.zip.GZIPOutputStream;

@Repository
@RequiredArgsConstructor
public class MI_0008_Repository {

   private final XxiRepositoryExecutor db;

   private static final String[] columns = {
     "itm_id",
     "external_uuid",
     "req_id",
     "created_at",
     "icusnum",
     "iyear",
     "icorr",
     "inum",
     "person_id",
     "sum_f",
     "sum_i",
     "last_name",
     "first_name",
     "middle_name",
     "ctzn_country_code",
     "inn",
     "doc_type_id",
     "doc_type_code",
     "doc_ser",
     "doc_num",
     "doc_issue_date",
     "doc_issuer_code",
     "doc_expire_date",
     "doc_issuer_name",
     "birth_date",
     "dly_sum_f", //  "y1:sum_f1;y2:sum_f2;"
     "dly_sum_i"
   };

   /**
    * <h6>Формирует GZIP/CSV payload из данных XXI.</h6>
    */
   public PayloadDto prepareData(long reqId )
   {
      return db.execute( "MI_0008.prepareData", U.toMap( "req_id", reqId ), tc -> buildPayload( tc, reqId ) );
   }

   /** */
   private PayloadDto buildPayload ( TaskContext tc, long reqId )
   {
      Path csvPath = null;
      boolean completed = false;

      try {

         csvPath = createTempFile(reqId);

         final CSVFormat csvFormat = CSVFormat.Builder.create().setHeader(columns).setDelimiter(',').build();

         int counter = writePayload( tc, reqId, csvPath, csvFormat );

         if( counter == 0 )
             throw Errors.emptyPayloadContainer( reqId, U.toMap("req_id", reqId) );

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
   private int writePayload( TaskContext tc, long reqId, Path csvPath, CSVFormat csvFormat )
   {
      try (
         GZIPOutputStream  out = new GZIPOutputStream( Files.newOutputStream(csvPath) );
         Writer         writer = new OutputStreamWriter( out, StandardCharsets.UTF_8 );
         CSVPrinter csvPrinter = new CSVPrinter( writer, csvFormat )
      )
      {
         Iterator<Object[]> iterator = createIterator( tc, reqId );

         int counter = 0;

         while( true )
         {
            Object[] row;

            try {

               if( !iterator.hasNext())
                   break;

               row = iterator.next();

            } catch (Exception exception) {
               throw Errors.dbError( "Ошибка чтения данных v_mi_0008_dat", exception, U.toMap("req_id", reqId) );
            }

            try {
               csvPrinter.printRecord(row);
            } catch (IOException exception) {
               throw Errors.payloadBuildFailed( "Ошибка записи CSV/GZIP payload", exception, U.toMap( "req_id", reqId, "row_number", counter + 1 ) );
            }

            counter++;
         }

         return counter;

      } catch( IOException exception ) {
         throw Errors.payloadBuildFailed(
           "Ошибка создания GZIP payload",
           exception,
           U.toMap( "req_id", reqId, "temp_file", csvPath.toString() )
         );
      }
   }


   /** */
   private Iterator<Object[]> createIterator( TaskContext tc, long reqId )
   {
      try {

         return new SQLDataSet<>( tc, Object[].class )
                 .sql( "select " + String.join(", ", columns) + " from v_mi_0008_dat" )
                 .wherePredicat( "req_id=" + reqId )
                 .rowMapper(
                         new IRowMapper<Object[]>() {
                            @Override
                            public Object[] mapRow( ResultSet rs, int rowNum ) throws SQLException {
                               Object[] row = new Object[columns.length];
                               for ( int i = 0, column = 1; i < columns.length; i++ )
                               {
                                  row[i] = rs.getObject(column++);
                               }
                               return row;
                            }
                         }
                 )
                 .createRSIterator(true);

      } catch (Exception exception) {
         throw Errors.dbError( "Ошибка выполнения запроса v_mi_0008_dat", exception, U.toMap("req_id", reqId) );
      }
   }

}