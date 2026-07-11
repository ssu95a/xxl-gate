package ru.inversion.msmev.xxi.repo;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import ru.inversion.dataset.DataSetException;
import ru.inversion.dataset.IRowMapper;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.msmev.error.Errors;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.sql.ResultSet;
import java.sql.SQLException;

@Repository
public class InfRepository {

    private final ObjectFactory<TaskContext> tcFactory;

    @Autowired
    public InfRepository( ObjectFactory<TaskContext> tcFactory ) {
        this.tcFactory = tcFactory;
    }

    /** */
    public PInf getInf( int infId )
    {
        try( TaskContext tc =  tcFactory.getObject() ) {

            final PInf inf =
                new SQLDataSet<>(tc,PInf.class)
                    .singleRow().wherePredicat( "inf_id=" + infId )
                        .execute()
                            .getCurrentRow();

            if( inf == null )
                throw new IllegalArgumentException("No data inf inst for Id: " + infId );

            return inf;
        }
        catch( DataSetException e ) {
            throw Errors.dbError( "Ошибка при выполнении запроса получения данных о mi_inf", e, U.toMap("inf_Id", infId) );
        }
    }

    /** */
    public Integer findInfIdByNamespace( String namespace )
    {
        if( S.isNullOrEmpty(namespace) )
            return null;

        try( TaskContext tc =  tcFactory.getObject() ) {

           return (Integer) new SQLDataSet<>(tc,Integer.class)
                   .sql("select inf_Id from mi_inf where namespace_inf = :ns")
                   .rowMapper((rs, rowNum) -> rs.getInt(1))
               .singleRow()
                   .set("ns", namespace )
               .execute()
                   .getCurrentRow();
        }
        catch( DataSetException e ) {
            throw Errors.dbError( "Ошибка при выполнении запроса получения данных о mi_inf", e, U.toMap("namespace", namespace) );
        }
    }
}
