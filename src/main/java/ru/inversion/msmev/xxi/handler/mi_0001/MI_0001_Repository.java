package ru.inversion.msmev.xxi.handler.mi_0001;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.http.MediaType;
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
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;

import java.util.zip.GZIPOutputStream;

@Repository
public class MI_0001_Repository {

    /** */
    final static private String[] columns
        = new String[] {
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

    public MI_0001_Repository(ObjectFactory<TaskContext> tcFactory ) {
        this.tcFactory = tcFactory;
    }

    /** */
    public PayloadDto prepareItemList( long reqId )
    {
        try( TaskContext tc = tcFactory.getObject() ) {

           final Path csvPath = Files.createTempFile( "xxl_0001_" + reqId + "_",".gz");

           final CSVFormat.Builder formatBuilder = CSVFormat.Builder.create( );
           formatBuilder.setHeader   ( columns );
           formatBuilder.setDelimiter(',');
           final CSVFormat  csvFormat  = formatBuilder.build();

           try (
               GZIPOutputStream out        = new GZIPOutputStream( Files.newOutputStream(csvPath) );
               Writer           writer     = new OutputStreamWriter( out );
               CSVPrinter       csvPrinter = new CSVPrinter( writer, csvFormat)
           )
           {
              Iterator<Object[]> rsIterator
                  = new SQLDataSet<>(tc, Object[].class)
                      .sql( "select " + String.join(", ", columns) + " from v_mi_0001" )
                          .wherePredicat( "req_Id=" + reqId )
                      .rowMapper( new IRowMapper<Object[]>() {
                         @Override
                         public Object[] mapRow(ResultSet rs, int rowNum) throws SQLException {
                            int nColumn = columns.length;
                            Object[] retRow = new Object[nColumn];
                            for( int i = 0, j = 1 ; i < nColumn; i++  )
                               retRow[i] = rs.getObject(j++);
                            return retRow;
                         }
                      })
                        .createRSIterator(true);

              final int[] counter = new int[1];
              counter[0] = 0;

              rsIterator.forEachRemaining( oa -> {
                 try {
                    csvPrinter.printRecord(oa);
                    counter[0]++;
                 } catch (IOException e) {
                    throw new UncheckedIOException(e);
                 }
              });

              if( counter[0] == 0 )
                  throw Errors.emptyPayloadContainer( reqId, new HashMap<>() );

              /*
              walker.walk( new IColumnValueConsumer() {
                 @Override
                 public void accept(int rowNum, int valueIndex, String columnKey, Object value) throws Exception {
                    csvPrinter.print(value);
                 }
                 @Override
                 public void afterRow(int rowNum) throws Exception {
                    csvPrinter.println();
                 }
              });
              */
           }

           return new PayloadDto( "application/gzip", csvPath, -1 );
        }
        catch ( XXLException x )
        {
           throw x;
        }
        catch ( Exception e ) {
            throw Errors.dbError (
               "Ошибка при выполнении запроса к БД получения данных v_mi_0001",
                e,
                U.toMap("req_Id", reqId)
            );
        }
    }
}
