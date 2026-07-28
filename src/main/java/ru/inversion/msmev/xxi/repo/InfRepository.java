package ru.inversion.msmev.xxi.repo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import ru.inversion.dataset.DataSetException;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.msmev.error.Errors;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

@Repository
public class InfRepository {

    private final XxiRepositoryExecutor db;

    @Autowired
    public InfRepository( XxiRepositoryExecutor db ) {
        this.db = db;
    }

    /** */
    public PInf getInf( int infId )
    {
        return db.execute (
            "InfRepository.getInf",
             U.toMap( "inf_id", infId ),
             tc -> {
                try {

                     final PInf inf = new SQLDataSet<>(tc,PInf.class).singleRow().wherePredicat( "inf_id=" + infId ).execute().getCurrentRow();

                     if( inf == null )
                         throw Errors.infNotFound(infId);

                     return inf;
                }
                catch( DataSetException e ) {
                    throw Errors.dbError(
                        "Ошибка при выполнении запроса получения данных о mi_inf",
                         e, U.toMap("inf_Id", infId) );
                }
            }
        );
    }

    /** */
    public Integer findInfIdByNamespace( String namespace )
    {
        if( S.isNullOrEmpty(namespace) )
            return null;

        return db.execute (
            "InfRepository.findInfIdByNamespace",
            U.toMap( "namespace", namespace ),
            tc -> {
                try {
                        return
                            (Integer) new SQLDataSet<>( tc, Integer.class )
                            .sql("select inf_Id from mi_inf where namespace_inf = :ns")
                            .rowMapper((rs, rowNum) -> rs.getInt(1))
                            .singleRow()
                                .set("ns", namespace )
                            .execute()
                                .getCurrentRow();                    }
                catch( DataSetException e ) {
                    throw Errors.dbError (
                       "Ошибка при выполнении запроса получения mi_inf.inf_id по namespace",
                        e, U.toMap("namespace_inf", namespace )
                    );
                }
            }
        );
    }
}
