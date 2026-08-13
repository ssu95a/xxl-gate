package ru.inversion.edo.xxl.mi.internal.handlers;

import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.mi.internal.InternalRequest;
import ru.inversion.edo.xxl.mi.internal.InternalRequestHandler;
import ru.inversion.edo.xxl.mi.internal.InternalResult;
import ru.inversion.utils.Checks;
import ru.inversion.utils.U;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Возвращает сведения о фактическом подключении XXL к БД XXI.
 */
@Component
public final class DatabaseConnectionInfoHandler implements InternalRequestHandler
{
   public static final String QUERY_TYPE = "DATABASE_CONNECTION_INFO";

   private static final String SQL = """
      select
         current_database()       as database_name,
         current_schema()         as database_schema,
         current_user             as database_user,
         inet_server_addr()::text as server_address,
         inet_server_port()       as server_port
      """;

   private final DataSource dataSource;

   /** */
   public DatabaseConnectionInfoHandler ( DataSource dataSource )
   {
      this.dataSource = Checks.Require.object( dataSource, "dataSource" );
   }

   @Override
   public Set<String> queryTypes()
   {
      return Set.of(QUERY_TYPE);
   }

   @Override
   public InternalResult handle(InternalRequest request )
   {
      try (
         Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(SQL)
      )
      {
         /*
            Не позволяем SQL зависнуть надолго.
          */
         statement.setQueryTimeout(5);

         try( ResultSet resultSet = statement.executeQuery() )
         {
            if( !resultSet.next() )
            {
               throw Errors.miInternalFailed(
                 "Database information query returned no row",
                 null,
                 U.toMap( "query_type", QUERY_TYPE, "message_id", request.messageId() )
               );
            }

            DatabaseMetaData metadata = connection.getMetaData( );

            Map<String, Object> data = new LinkedHashMap<>();

            putIfNotNull( data, "jdbcUrl",        metadata.getURL() );
            putIfNotNull( data, "databaseUser",   resultSet.getString("database_user") );
            putIfNotNull( data, "databaseName",   resultSet.getString("database_name") );
            putIfNotNull( data, "databaseSchema", resultSet.getString("database_schema"));
            putIfNotNull( data, "serverAddress",  resultSet.getString("server_address") );
            Integer serverPort = resultSet.getObject( "server_port", Integer.class );
            putIfNotNull( data, "serverPort", serverPort );
            putIfNotNull( data, "databaseProduct", metadata.getDatabaseProductName() );
            putIfNotNull( data, "databaseVersion", metadata.getDatabaseProductVersion() );

            return InternalResult.ok(data);
         }
      }
      catch( SQLException exception )
      {
         throw Errors.dbError(
                 "Read XXL DB connection information",
                 exception,
                 U.toMap( "query_type", QUERY_TYPE, "message_id", request.messageId() )
         );
      }
   }


   /** */
   private static void putIfNotNull( Map<String, Object> target, String key, Object value )
   {
      if( value != null )
          target.put(key, value);
   }
}