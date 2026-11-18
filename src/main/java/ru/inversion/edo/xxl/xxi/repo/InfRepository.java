package ru.inversion.edo.xxl.xxi.repo;

import org.springframework.stereotype.Repository;
import ru.inversion.dataset.DataSetException;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.dataset.impl.XXIDsDao;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Repository
public class InfRepository {

    private final XxiRepositoryExecutor db;

    public InfRepository( XxiRepositoryExecutor db ) {
        this.db = db;
    }

    /**
    * Единый mapping MI_INF для Oracle/PG.
    * Oracle transport settings находятся в MI_Prp, а не в колонках MI_INF.
    */
    public PInf getInf(int infId)
    {
       return db.execute(
             "InfRepository.getInf",
             U.toMap("inf_id", infId),
             tc -> {
                String sql = XXIDsDao.getNativeQuery(
                      tc, PInfSql.class, "PInfSql.byId");
                if (sql == null || sql.isBlank())
                   throw new SQLException("MI_INF native SQL is missing");

                try (PreparedStatement ps = tc.getConnection().prepareStatement(sql))
                {
                   ps.setInt(1, infId);
                   try (ResultSet rs = ps.executeQuery())
                   {
                      if (!rs.next())
                         throw Errors.infNotFound(infId);
                      PInf inf = mapInf(rs);
                      if (rs.next())
                         throw new SQLException("MI_INF returned multiple rows: inf_id=" + infId);
                      return inf;
                   }
                }
             });
    }

    /** Статусы initiator_cd в базе: -1 инициатор, +1 ответчик. */
    static PInf mapInf(ResultSet rs) throws SQLException
    {
       PInf inf = new PInf();

       int infId = rs.getInt("inf_id");
       if (rs.wasNull() || infId <= 0)
          throw new SQLException("MI_INF invalid INF_ID");
       inf.setInfId(infId);

       int wspId = rs.getInt("wsp_id");
       if (rs.wasNull() || wspId <= 0)
          throw new SQLException("MI_INF invalid WSP_ID: inf_id=" + infId);
       inf.setWspId(wspId);

       int initiator = rs.getInt("initiator_cd");
       if (rs.wasNull() || (initiator != -1 && initiator != 1))
          throw new SQLException("MI_INF invalid INITIATOR_CD: inf_id=" + infId);
       inf.setInitiator(initiator == -1);

       inf.setName(rs.getString("name_inf"));
       inf.setNamespace(rs.getString("namespace_inf"));
       inf.setRequestQueue(rs.getString("request_queue"));
       inf.setResponseQueue(rs.getString("response_queue"));
       return inf;
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
