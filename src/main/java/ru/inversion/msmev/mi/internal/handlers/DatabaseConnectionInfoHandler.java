package ru.inversion.msmev.mi.internal.handlers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.mi.internal.MiInternalRequest;
import ru.inversion.msmev.mi.internal.MiInternalRequestHandler;
import ru.inversion.msmev.mi.internal.MiInternalResult;
import ru.inversion.utils.Checks;
import ru.inversion.utils.U;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Возвращает сведения о фактическом подключении XXL к БД XXI.
 */
@Component
public final class DatabaseConnectionInfoHandler implements MiInternalRequestHandler
{
   public static final String QUERY_TYPE = "DATABASE_CONNECTION_INFO";

   private static final String SQL = """
      select
         current_database()       as database_name,
         current_user             as database_user,
         inet_server_addr()::text as server_address,
         inet_server_port()       as server_port
      """;

   private static final Pattern PASSWORD_QUERY_PARAMETER =
           Pattern.compile(
                   "(?i)([?&;](?:password|pwd|pass|sslpassword)=)[^&;]*"
           );

   private static final Pattern PASSWORD_IN_AUTHORITY =
           Pattern.compile(
                   "(://[^:/?#]+:)[^@/?#]+@"
           );

   private final DataSource dataSource;
   private String databaseEnvironment;

   /** */
   public DatabaseConnectionInfoHandler (
      DataSource dataSource
   )
   {
      this.dataSource = Checks.Require.object( dataSource, "dataSource" );
   }

   @Override
   public Set<String> queryTypes()
   {
      return Set.of(QUERY_TYPE);
   }

   @Override
   public MiInternalResult handle( MiInternalRequest request )
   {
      try (
         Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(SQL)
      )
      {
         /*
          * MI ждёт sendSync около 7 секунд.
          * Не позволяем диагностическому SQL зависнуть
          * дольше основного транспортного timeout.
          */
         statement.setQueryTimeout(5);

         try( ResultSet resultSet = statement.executeQuery() )
         {
            if( !resultSet.next() )
            {
               throw Errors.miServiceFailed(
                       "Database information query returned no row",
                       null,
                       U.toMap(
                               "query_type", QUERY_TYPE,
                               "message_id", request.messageId()
                       )
               );
            }

            DatabaseMetaData metadata = connection.getMetaData();

            Map<String, Object> data = new LinkedHashMap<>();

            data.put ( "environment", databaseEnvironment );

            putIfNotNull(
                    data,
                    "jdbcUrl",
                    sanitizeJdbcUrl(metadata.getURL())
            );

            putIfNotNull(
                    data,
                    "databaseUser",
                    resultSet.getString("database_user")
            );

            putIfNotNull(
                    data,
                    "databaseName",
                    resultSet.getString("database_name")
            );

            putIfNotNull(
                    data,
                    "databaseSchema",
                    resultSet.getString("database_schema")
            );

            putIfNotNull(
                    data,
                    "serverAddress",
                    resultSet.getString("server_address")
            );

            Integer serverPort =
                    resultSet.getObject(
                            "server_port",
                            Integer.class
                    );

            putIfNotNull(
                    data,
                    "serverPort",
                    serverPort
            );

            putIfNotNull(
                    data,
                    "databaseProduct",
                    metadata.getDatabaseProductName()
            );

            putIfNotNull(
                    data,
                    "databaseVersion",
                    metadata.getDatabaseProductVersion()
            );

            return MiInternalResult.ok(data);
         }
      }
      catch( SQLException exception )
      {
         throw Errors.dbError(
                 "Failed to read XXL database connection information",
                 exception,
                 U.toMap(
                         "query_type", QUERY_TYPE,
                         "message_id", request.messageId()
                 )
         );
      }
   }

   private static String normalizeEnvironment(
           String value
   )
   {
      if( value == null || value.isBlank() )
         return "UNKNOWN";

      String normalized =
              value.trim().toUpperCase(Locale.ROOT);

      return switch( normalized )
      {
         case "TEST", "PROD" -> normalized;
         default -> "UNKNOWN";
      };
   }

   private static String sanitizeJdbcUrl(
           String jdbcUrl
   )
   {
      if( jdbcUrl == null )
         return null;

      String sanitized =
              PASSWORD_QUERY_PARAMETER
                      .matcher(jdbcUrl)
                      .replaceAll("$1***");

      return PASSWORD_IN_AUTHORITY
              .matcher(sanitized)
              .replaceAll("$1***@");
   }

   private static void putIfNotNull(
           Map<String, Object> target,
           String key,
           Object value
   )
   {
      if( value != null )
         target.put(key, value);
   }
}