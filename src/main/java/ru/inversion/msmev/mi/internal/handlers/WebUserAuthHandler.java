package ru.inversion.msmev.mi.internal.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.db.session.xxi.XXIConnectorException;
import ru.inversion.msmev.TaskContextFactory;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.mi.internal.MiInternalRequest;
import ru.inversion.msmev.mi.internal.MiInternalRequestHandler;
import ru.inversion.msmev.mi.internal.MiInternalResult;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.net.PasswordAuthentication;
import java.sql.SQLException;
import java.util.*;

/** */
@Component
@Slf4j
public class WebUserAuthHandler implements MiInternalRequestHandler {

   public static final String QUERY_TYPE = "WEB-AUTH";

   public static final int ACT_ID = 6138;

   private final TaskContextFactory taskContextFactory;

   public WebUserAuthHandler( TaskContextFactory tcf ) {
      taskContextFactory = tcf;
   }

   @Override
   public Set<String> queryTypes() {
      return Set.of( QUERY_TYPE );
   }


   /** */
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


   /** */
   Integer queryAccess( TaskContext tc )
   {
      return
        SQLCallBuilder.NEW(tc)
          .url(WebUserAuthHandler.class.getResource("plsql/def.xml"))
          .name("isAct")
        .build()
          .set("ACT_ID", ACT_ID)
        .execute()
          .<Integer>getReturnValue();
   }


   /** */
   private MiInternalResult check( PasswordAuthentication authentication, MiInternalRequest request )
   {
      TaskContext tc;

      try
      {
         tc = taskContextFactory.create( authentication.getUserName(), String.valueOf(authentication.getPassword() ));
      }
      catch( Exception exception ) {
         return authenticationFailure( request, exception );
      }

      try( tc )
      {
         Integer result = queryAccess(tc);

         if( Integer.valueOf(1).equals(result) )
             return MiInternalResult.ok( "SUCCESS", "OK", U.toMap("valid", Boolean.TRUE) );

         if( Integer.valueOf(0).equals(result) )
             return MiInternalResult.error( "ACCESS_DENIED", "Пользователь не имеет доступа к Web-модулю", U.toMap("valid", Boolean.FALSE) );

         throw new IllegalStateException( "Unexpected odb_Access_Is_Act result: " + result );
      }
      catch( Exception exception ) {
         return databaseUnavailable( request, exception );
      }
   }


   /** */
   private MiInternalResult databaseUnavailable( MiInternalRequest request, Exception exception )
   {
      log.error (
         "MI INTERNAL XXI database error: messageId={}, failureClass={}, message={}",
         request.messageId(),
         exception.getClass().getName(),
         exception.getMessage(),
         exception
      );

      return MiInternalResult.error( "DATABASE_UNAVAILABLE", "База данных XXI недоступна", U.toMap("valid", Boolean.FALSE) );
   }


   /** */
   private MiInternalResult badCredentials( MiInternalRequest request, XXIConnectorException exception )
   {
      log.info (
        "MI INTERNAL XXI authentication rejected: messageId={}, reason={}",
        request.messageId(), exception.getReason()
      );

      return MiInternalResult.error( "BAD_CREDENTIALS", "Неверное имя пользователя или пароль", U.toMap( "valid", Boolean.FALSE ) );
   }

   /** */
   private MiInternalResult accessDenied( MiInternalRequest request, XXIConnectorException exception )
   {
      log.info(
              "MI INTERNAL XXI access denied: messageId={}, reason={}",
              request.messageId(),
              exception.getReason()
      );
      return MiInternalResult.error( "ACCESS_DENIED", "Пользователь не имеет доступа к Web-модулю", U.toMap( "valid", Boolean.FALSE ) );
   }


   private MiInternalResult authenticationFailure( MiInternalRequest request, Exception exception )
   {
      XXIConnectorException xxiException = findXxiConnectorException(exception);

      if( xxiException == null )
         return databaseUnavailable( request, exception );

      return switch( xxiException.getReason() )
      {
         case AUTH_FAILED ->
                 badCredentials( request, xxiException );

         case USER_NOT_FOUND,
              NO_XXI_PASSWORD,
              PASSWORD_EXPIRED,
              PASSWORD_CHANGE_REQUIRED,
              ACCOUNT_LOCKED,
              AUTH_METHOD_MISMATCH ->
                 accessDenied( request, xxiException );

         case DB_UNAVAILABLE,
              TECHNICAL_BREAK,
              LICENCE_ERROR,
              ENCRYPTION_ERROR,
              DECRYPTION_ERROR,
              INTERNAL_ERROR ->
                 databaseUnavailable( request, xxiException );
      };
   }

   /** */
   private static XXIConnectorException findXxiConnectorException( Throwable exception )
   {
      Set<Throwable> visited = Collections.newSetFromMap( new IdentityHashMap<>() );

      Deque<Throwable> queue = new ArrayDeque<>();

      queue.add(exception);

      while( !queue.isEmpty() )
      {
         Throwable current = queue.removeFirst();

         if( !visited.add(current) )
              continue;

         if( current instanceof XXIConnectorException xxiException )
             return xxiException;

         Throwable cause = current.getCause();

         if( cause != null )
             queue.addLast(cause);

         for( Throwable suppressed : current.getSuppressed() )
              queue.addLast(suppressed);

         if( current instanceof SQLException sqlException )
         {
            SQLException next = sqlException.getNextException();

            if( next != null )
                queue.addLast(next);
         }
      }

      return null;
   }
}


