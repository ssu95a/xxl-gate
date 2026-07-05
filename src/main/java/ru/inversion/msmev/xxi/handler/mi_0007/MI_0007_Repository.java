package ru.inversion.msmev.xxi.handler.mi_0007;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import ru.inversion.dataset.IRowMapper;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.xxi.repo.XxiRepositoryExecutor;
import ru.inversion.utils.U;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Repository
@RequiredArgsConstructor
public class MI_0007_Repository {

   private static final String[] COLUMNS = {
      "last_name",
      "first_name",
      "middle_name",
      "doc_ser",
      "doc_num",
      "birth_date",
      "region_code",
      "external_uuid",
      "itm_id"
   };

   private final XxiRepositoryExecutor db;

   public List<Map<String, Object>> getItemsList(long reqId)
   {
      return db.execute(
              "MI_0007.getItemsList",
              U.toMap( "req_id", reqId, "source", "v_mi_0007" ),
              tc -> {

                 Map<String, Object> rowPrototype = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );
                 @SuppressWarnings("unchecked")
                 Class<? extends Map<String, Object>> rowClass = (Class<? extends Map<String, Object>>)rowPrototype.getClass();

                 List<Map<String, Object>> rows
                     = new SQLDataSet<Map<String, Object>>(tc)
                        .rowClass(rowClass)
                        .sql( "select " + String.join( ", ", COLUMNS ) + " from v_mi_0007" )
                        .rowMapper(
                             new IRowMapper<Map<String, Object>>() {
                                @Override
                                public Map<String, Object> mapRow( ResultSet rs, int rowNum ) throws SQLException
                                {
                                   Map<String, Object> row = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );
                                   for( String column : COLUMNS )
                                        row.put( column, rs.getObject(column) );
                                   return row;
                                }
                             }
                        )
                        .wherePredicat( "req_id=" + reqId )
                        .queryAllRows()
                     .execute()
                        .getRows();

                 if (rows == null || rows.isEmpty()) {
                    throw Errors.emptyPayloadContainer(
                            reqId,
                            U.toMap( "req_id", reqId, "source", "v_mi_0007" )
                    );
                 }
                 return rows;
              }
      );
   }
}