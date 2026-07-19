package ru.inversion.msmev.mi.internal.handlers;

import org.springframework.stereotype.Component;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.msmev.TaskContextFactory;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.mi.internal.MiInternalRequest;
import ru.inversion.msmev.mi.internal.MiInternalRequestHandler;
import ru.inversion.msmev.mi.internal.MiInternalResult;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.net.PasswordAuthentication;
import java.sql.*;
import java.util.Map;
import java.util.Set;

/** */
@Component
public class WebUserAuthHandler implements MiInternalRequestHandler {

   public static final String QUERY_TYPE = "AUTH";

   private final TaskContextFactory taskContextFactory;

   public WebUserAuthHandler( TaskContextFactory tcf ) {
      taskContextFactory = tcf;
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

   private static final String SQL = "select 1 from user_roles u where u.login = ? and u.role_name = 'WEB_OPERATOR'";

   /** */
   private MiInternalResult check( PasswordAuthentication authentication, MiInternalRequest request )
   {
      try (
         TaskContext tc = taskContextFactory.create( authentication.getUserName(), String.valueOf(authentication.getPassword() ) );
      )
      {
         SQLDataSet<Boolean> dsCheck =
            new SQLDataSet<>(tc, Boolean.class)
               .sql(SQL).set(0,authentication.getUserName())
                    .execute();

         if( dsCheck.isEmpty() )
            return MiInternalResult.ok(U.toMap("valid", Boolean.FALSE ));

         return MiInternalResult.ok(U.toMap("valid", Boolean.TRUE ));
      }
      catch( Exception exception )
      {
         throw Errors.dbError(
              "Failed to check WEB_OPERATOR access",
              exception,
              U.toMap( "query_type", QUERY_TYPE, "message_id", request.messageId() )
         );
      }
   }
}


