package ru.inversion.msmev.js;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.stereotype.Repository;
import ru.inversion.dataset.DataSetException;
import ru.inversion.dataset.IRowMapper;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.db.DBUniqueResult;
import ru.inversion.db.DbNoDataFoundException;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.Pair;
import ru.inversion.utils.converter.TypeConverter;
import ru.inversion.utils.io.RawCAW;

import javax.persistence.NamedNativeQuery;
import java.io.IOException;
import java.io.Reader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

@Repository
public class JSRepository {

    private final ObjectFactory<TaskContext> tcFactory;

    public JSRepository( ObjectFactory<TaskContext> tcFactory ) {
        this.tcFactory = tcFactory;
    }

    /** */
    public LocalDateTime getJSDateTime( int infId, int type ) {
        try( TaskContext tc = tcFactory.getObject() ) {
             return
                 new SQLDataSet<LocalDateTime>(tc,LocalDateTime.class)
                    .sql("SELECT ts_body FROM mi_inf_js c WHERE c.inf_id = ? AND c.js_type = ?")
                         .set(0,infId)
                         .set(1,type)
                         .rowMapper(new IRowMapper<LocalDateTime>() {
                             @Override
                             public LocalDateTime mapRow(ResultSet rs, int rowNum) throws SQLException {
                                 return TypeConverter.convert( rs.getObject(1), LocalDateTime.class );
                             }
                         }).queryAllRows()
                    .execute()
                         .getCurrentRow();
        } catch (DataSetException e) {
            throw new RuntimeException(e);
        }
    }

    /** */
    public Pair<Reader,LocalDateTime> getJSBody( int infId, int type ) {

        try( TaskContext tc = tcFactory.getObject() ) {

            final var rm = new IRowMapper<Pair<Reader, LocalDateTime>>() {
                @Override
                public Pair<Reader, LocalDateTime> mapRow(ResultSet rs, int rowNum) throws SQLException {

                    try (RawCAW rcw = new RawCAW()) {
                        try {
                            rcw.write(rs.getCharacterStream(1));
                        } catch (IOException e) {
                            throw new SQLException("Ошибка при чтении тела скрипта из таблицы", e);
                        }
                        return Pair.makePair(
                                rcw.reader(),
                                TypeConverter.convert(rs.getObject(2), LocalDateTime.class)
                        );
                    }
                }
            };

            final Pair<Reader, LocalDateTime> m = new Pair<Reader, LocalDateTime>();

            return
                new SQLDataSet<Pair<Reader, LocalDateTime>>(tc)
                    .rowClass((Class<? extends Pair<Reader, LocalDateTime>>) m.getClass())
                    .sql("SELECT js_body, ts_body FROM mi_inf_js c WHERE c.inf_id = ? AND c.js_type = ?")
                        .set(0,infId)
                        .set(1,type)
                    .rowMapper(rm).queryAllRows()
                        .execute()
                    .getCurrentRow();

        } catch( DataSetException e ) {
            throw new RuntimeException(e);
        }
    }
}
