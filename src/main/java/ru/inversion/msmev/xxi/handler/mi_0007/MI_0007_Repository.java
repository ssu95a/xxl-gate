package ru.inversion.msmev.xxi.handler.mi_0007;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.stereotype.Repository;
import ru.inversion.dataset.DataSetException;
import ru.inversion.dataset.IRowMapper;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.msmev.error.Errors;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.U;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Repository
public class MI_0007_Repository {

    /** */
    final static private String[] columns = new String[]{
        "last_name", "first_name", "middle_name",
        "doc_ser", "doc_num", "birth_date",
        "region_code", "external_uuid", "itm_id"
    };

    private final ObjectFactory<TaskContext> tcFactory;

    public MI_0007_Repository( ObjectFactory<TaskContext> tcFactory ) {
        this.tcFactory = tcFactory;
    }

    /** */
    public List<Map<String,Object>> getItemsList(long reqId )
    {
        try( TaskContext tc = tcFactory.getObject() ) {
            final Map<String,Object> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
             return
             new SQLDataSet<Map<String,Object>>(tc )
                .rowClass((Class<? extends Map<String, Object>>) m.getClass())
                .sql("select * from v_mi_0007")
                    .rowMapper(new IRowMapper<Map<String,Object>>() {
                        @Override
                        public Map<String,Object> mapRow( ResultSet rs, int rowNum ) throws SQLException {
                            final Map<String,Object> retMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                            for( String column : columns )
                                 retMap.put( column, rs.getObject(column) );
                            return retMap;
                        }
                    })
                    .wherePredicat("req_Id=" + reqId ) //+ " -- and inf_id=74 and state_cd=0")
                        .queryAllRows()
                            .execute().getRows();
        }
        catch ( DataSetException ex ) {
            throw Errors.dbError( "Error on execute getItemsList", ex, U.toMap("sql", "select * from v_mi_0007 where req_id =  " + reqId, "req_id", reqId ));
        }
    }
}
