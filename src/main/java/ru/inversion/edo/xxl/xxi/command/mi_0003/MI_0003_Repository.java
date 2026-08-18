package ru.inversion.edo.xxl.xxi.command.mi_0003;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import ru.inversion.dataset.IRowMapper;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.utils.U;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Repository
@RequiredArgsConstructor
public class MI_0003_Repository {

   private final XxiRepositoryExecutor db;

   public List<Map<String, Object>> getItemsList(long reqId)
   {
      return db.execute(
              "MI_0003.getItemsList",
              U.toMap( "req_id", reqId, "source", "v_mi_0007" ),
              tc -> {

                 Map<String, Object> rowPrototype = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );
                 @SuppressWarnings("unchecked")
                 Class<? extends Map<String, Object>> rowClass = (Class<? extends Map<String, Object>>)rowPrototype.getClass();

                 List<Map<String, Object>> rows
                     = new SQLDataSet<Map<String, Object>>(tc)
                        .rowClass(rowClass)
                        .sql( "select itm_id, inn, ogrn from v_mi_0003" )
                        .rowMapper(
                             new IRowMapper<Map<String, Object>>() {
                                @Override
                                public Map<String, Object> mapRow( ResultSet rs, int rowNum ) throws SQLException
                                {
                                   Map<String, Object> row = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );
                                   int i = 1;
                                   row.put( "itm_id", rs.getObject(i++) );
                                   row.put( "inn",    rs.getString(i++) );
                                   row.put( "ogrn",   rs.getString(i) );
                                   return row;
                                }
                             }
                        )
                        .wherePredicat( "req_id=" + reqId )
                        .queryAllRows()
                     .execute()
                        .getRows();

                 if( rows == null || rows.isEmpty() )
                    throw Errors.emptyPayloadContainer( reqId, U.toMap( "req_id", reqId, "source", "v_mi_0003" ) );

                 return rows;
              }
      );
   }
}