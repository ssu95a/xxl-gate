package ru.inversion.msmev.mi.internal.handlers;

import org.springframework.stereotype.Component;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.mi.internal.MiInternalRequest;
import ru.inversion.msmev.mi.internal.MiInternalRequestHandler;
import ru.inversion.msmev.mi.internal.MiInternalResult;
import ru.inversion.utils.Checks;
import ru.inversion.utils.U;

import javax.sql.DataSource;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * Возвращает данные из таблицы SMR
 */
@Component
public final class SmrDataHandler implements MiInternalRequestHandler
{
   public static final String QUERY_TYPE = "SMR";

   private static final String SQL =
      "select ( select ccusksiva from vcus where icusnum = smr.ismrcus ) ccusksiva, csmrname, csmraddr, csmrmfo8, csmrbic, ismrinn, idsmr, ismrfil from smr";

   private final DataSource dataSource;

   /** */
   public SmrDataHandler( DataSource dataSource )
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
         statement.setQueryTimeout(5);

         try( ResultSet resultSet = statement.executeQuery() )
         {
            if( !resultSet.next() )
            {
               throw Errors.miServiceFailed (
                 "Database information query returned no row",
                 null,
                 U.toMap( "query_type", QUERY_TYPE, "message_id", request.messageId() )
               );
            }

            final ResultSetMetaData metaData = resultSet.getMetaData();
            final LinkedHashMap<String,Object> data = new LinkedHashMap<>();

            int nCount = metaData.getColumnCount();

            for( int i = 1; i <= nCount; i++ )
                 data.put( metaData.getColumnName(i), resultSet.getObject(i) );

            return MiInternalResult.ok(data);
         }
      }
      catch( SQLException exception )
      {
         throw Errors.dbError(
                 "Failed to read XXL database connection information",
                 exception,
                 U.toMap( "query_type", QUERY_TYPE, "message_id", request.messageId() )
         );
      }
   }
}