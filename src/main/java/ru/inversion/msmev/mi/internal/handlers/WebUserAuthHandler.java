package ru.inversion.msmev.mi.internal.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.msmev.TaskContextFactory;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.mi.internal.MiInternalRequest;
import ru.inversion.msmev.mi.internal.MiInternalRequestHandler;
import ru.inversion.msmev.mi.internal.MiInternalResult;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.net.PasswordAuthentication;
import java.util.Map;
import java.util.Set;

/** */
@Component
@Slf4j
public class WebUserAuthHandler implements MiInternalRequestHandler {

   public static final String QUERY_TYPE = "WEB-AUTH";

   public static final int ACT_ID = 0;

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
   Integer queryAccess(TaskContext tc )
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
      catch( Exception exception )
      {
         if( isBadCredentials(exception) )
            return MiInternalResult.error( "BAD_CREDENTIALS", "Неверное имя пользователя или пароль", U.toMap("valid", Boolean.FALSE) );

         return databaseUnavailable(request, exception);
      }

      try( tc )
      {
         Integer result = queryAccess(tc);

         if( result != null && result != 0 )
            return MiInternalResult.ok( "SUCCESS", "OK", U.toMap("valid", Boolean.TRUE) );

         return MiInternalResult.error( "ACCESS_DENIED", "Пользователь не имеет доступа к Web-модулю", U.toMap("valid", Boolean.FALSE) );
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
   private boolean isBadCredentials( Exception exception ) {

      // STUB

      log.error("BadCredentials", exception );
      return false;
   }
}


