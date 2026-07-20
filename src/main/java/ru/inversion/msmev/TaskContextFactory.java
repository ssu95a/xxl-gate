package ru.inversion.msmev;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Configuration
public class TaskContextFactory
{
    private final String jdbcUrl;

    /** */
    public TaskContextFactory( DataSource dataSource )
    {
        jdbcUrl = resolveJdbcUrl(dataSource);
    }

    /** */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public TaskContext taskContext()
    {
        return new TaskContext();
    }


    /** <h5>Создает контекст для другого пользователя</h5>
     *  <p>
     *  Т.к. хикари глушит getConnection(String username, String password) через:
     *  {@code public Connection getConnection(String username, String password) throws SQLException { throw new SQLFeatureNotSupportedException(); } }
     *  <p>
     *  Пришлось по его URL строить прямое соединение к БД, для  другого пользователя
     */
    public TaskContext create( String login, String password )
    {
        return new TaskContext( login, password, jdbcUrl );
    }

    /** */
    private String resolveJdbcUrl( DataSource dataSource )
    {
        if( dataSource instanceof HikariDataSource hikari )
        {
            String url = hikari.getJdbcUrl();
            if( !S.isNullOrEmpty(url) )
                return url;
        }

        try
        {
            if( dataSource.isWrapperFor(HikariDataSource.class) )
            {
                HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
                String url = hikari.getJdbcUrl();

                if( !S.isNullOrEmpty(url) )
                    return url;
            }
        }
        catch( SQLException ignored )
        {
        }

        /*
         * Универсальный запасной путь.
         * Берём фактический URL из metadata штатного соединения.
         */
        try( Connection connection = dataSource.getConnection() )
        {
            String url = connection.getMetaData().getURL();

            if( S.isNullOrEmpty(url) )
                throw new IllegalStateException( "JDBC URL is empty" );

            return url;
        }
        catch( SQLException exception )
        {
            throw new IllegalStateException( "Failed to resolve JDBC URL", exception );
        }
    }
}