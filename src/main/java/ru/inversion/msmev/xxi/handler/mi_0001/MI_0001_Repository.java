package ru.inversion.msmev.xxi.handler.mi_0001;

import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.stereotype.Repository;
import ru.inversion.dataset.IRowMapper;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.error.XXLException;
import ru.inversion.msmev.transport.PayloadDto;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.U;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;

import java.util.zip.GZIPOutputStream;

@Repository
@RequiredArgsConstructor
public class MI_0001_Repository {

   private static final String[] columns = {
           "itm_id",
           "req_id",
           "created_at",
           "person_id",
           "icusnum",
           "item_uuid",
           "last_name",
           "first_name",
           "middle_name",
           "birth_date",
           "birth_place",
           "doc_type_id",
           "doc_ser",
           "doc_num",
           "doc_issue_date",
           "doc_issuer_code",
           "doc_issuer_name"
   };

   private final ObjectFactory<TaskContext> tcFactory;

   /** */
   public PayloadDto prepareItemList( long reqId )
   {
      Path csvPath = null;
      boolean completed = false;

      try {

         try( TaskContext tc = tcFactory.getObject() )
         {
            csvPath = createTempFile(reqId);

            CSVFormat csvFormat = CSVFormat.Builder.create().setHeader(columns).setDelimiter(',').build();

            int counter = writePayload( tc, reqId, csvPath, csvFormat );

            if (counter == 0) {
               throw Errors.emptyPayloadContainer(
                       reqId,
                       U.toMap("req_id", reqId)
               );
            }
         }

         completed = true;

         return new PayloadDto( "application/gzip", csvPath, -1 );

      } catch (XXLException exception) {
         throw exception;
      } catch (Exception exception) {
         throw Errors.dbError(
                 "Ошибка работы с БД при формировании payload v_mi_0001",
                 exception,
                 U.toMap("req_id", reqId)
         );

      } finally {
         if( !completed)
             deleteQuietly(csvPath);
      }
   }

   /** */
   private Path createTempFile(long reqId)
   {
      try {

         return Files.createTempFile( "xxl_0001_" + reqId + "_", ".gz" );

      } catch (IOException exception) {
         throw Errors.payloadBuildFailed(
                 "Не удалось создать временный файл payload",
                 exception,
                 U.toMap("req_id", reqId)
         );
      }
   }

   /** */
   private int writePayload( TaskContext tc, long reqId, Path csvPath, CSVFormat csvFormat )
   {
      try (
              GZIPOutputStream out =
                      new GZIPOutputStream(
                              Files.newOutputStream(csvPath)
                      );

              Writer writer =
                      new OutputStreamWriter(
                              out,
                              StandardCharsets.UTF_8
                      );

              CSVPrinter csvPrinter =
                      new CSVPrinter(writer, csvFormat)
      )
      {
         Iterator<Object[]> iterator = createIterator(tc, reqId);

         int counter = 0;

         while(true)
         {
            Object[] row;

            try {

               if (!iterator.hasNext())
                  break;

               row = iterator.next();

            } catch (Exception exception) {
               throw Errors.dbError( "Ошибка чтения данных v_mi_0001", exception, U.toMap("req_id", reqId) );
            }

            try {
               csvPrinter.printRecord(row);
            }
            catch (IOException exception) {
               throw Errors.payloadBuildFailed( "Ошибка записи CSV/GZIP payload", exception, U.toMap( "req_id", reqId, "row_number", counter + 1 ) );
            }

            counter++;
         }

         return counter;

      } catch (IOException exception) {
         throw Errors.payloadBuildFailed ( "Ошибка создания GZIP payload", exception, U.toMap( "req_id", reqId, "temp_file", csvPath.toString() ) );
      }
   }

   /** */
   private Iterator<Object[]> createIterator(
           TaskContext tc,
           long reqId
   ) {
      try {

         return new SQLDataSet<>(tc, Object[].class)
           .sql( "select " + String.join(", ", columns) + " from v_mi_0001" )
           .wherePredicat("req_id=" + reqId)
           .rowMapper(new IRowMapper<Object[]>() {
              @Override
              public Object[] mapRow(
                      ResultSet rs,
                      int rowNum
              ) throws SQLException {
                 Object[] row = new Object[columns.length];
                 for( int i = 0, column = 1; i < columns.length; i++ ) {
                      row[i] = rs.getObject(column++);
                 }
                 return row;
              }
           })
           .createRSIterator(true);

      }
      catch (Exception exception) {
         throw Errors.dbError(
              "Ошибка выполнения запроса v_mi_0001",
              exception, U.toMap("req_id", reqId)
         );
      }
   }

   private void deleteQuietly(Path path)
   {
      if (path == null)
         return;

      try {
         Files.deleteIfExists(path);
      } catch (IOException ignored) {
         /*
          * Ошибка удаления временного файла не должна
          * затереть исходную ошибку формирования payload.
          */
      }
   }
}