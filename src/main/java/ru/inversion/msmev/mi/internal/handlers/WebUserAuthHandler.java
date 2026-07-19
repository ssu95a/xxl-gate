package ru.inversion.msmev.mi.internal.handlers;

import org.springframework.stereotype.Component;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.mi.internal.MiInternalRequest;
import ru.inversion.msmev.mi.internal.MiInternalRequestHandler;
import ru.inversion.msmev.mi.internal.MiInternalResult;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import javax.sql.DataSource;
import java.net.PasswordAuthentication;
import java.sql.*;
import java.util.Map;
import java.util.Set;

/** */
@Component
public class WebUserAuthHandler implements MiInternalRequestHandler {

   public static final String QUERY_TYPE = "AUTH";

   private final DataSource dataSource;

   public WebUserAuthHandler(DataSource dataSource) {
      this.dataSource = dataSource;
   }

   @Override
   public Set<String> queryTypes() {
      return Set.of( QUERY_TYPE );
   }

   @Override
   public MiInternalResult handle( MiInternalRequest request ) {

      PasswordAuthentication authentication = readPayload(request);
      return check( authentication, request );
   }


   /** */
   private PasswordAuthentication readPayload( MiInternalRequest request )
   {
      if( request.params() == null || request.params().isEmpty() )
          throw Errors.miServicePayloadBadFormat("'params' is empty", request.dump() );

      final Map<String,Object> params = request.params();

      String l = (String) params.get("login");
      String p = (String) params.get("password");

      if( S.isNullOrEmpty(l) )
          throw Errors.miServicePayloadBadFormat( "'login' is empty", request.dump() );
      if( S.isNullOrEmpty(p) )
          throw Errors.miServicePayloadBadFormat( "'password' is empty", request.dump() );

      return new PasswordAuthentication( l, p.toCharArray() );

   }

   private static final String SQL = "select 1 from user_roles u where a.login = ? and role_name = 'WEB_OPERATOR'";

   /** */
   private MiInternalResult check( PasswordAuthentication authentication, MiInternalRequest request )
   {
      try (
              Connection connection = dataSource.getConnection( authentication.getUserName(), String.valueOf(authentication.getPassword() ) );
              PreparedStatement statement = connection.prepareStatement(SQL)
      )
      {
         statement.setString( 1, authentication.getUserName() );
         statement.setQueryTimeout(5);

         try( ResultSet resultSet = statement.executeQuery() )
         {
            if( !resultSet.next() )
               return MiInternalResult.ok(U.toMap("valid", Boolean.FALSE ));

            return MiInternalResult.ok(U.toMap("valid", Boolean.TRUE ));
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


