package ru.inversion.msmev.xxi.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.stereotype.Component;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.error.XXLException;
import ru.inversion.msmev.util.Attrs;
import ru.inversion.msmev.util.XxlLog;
import ru.inversion.msmev.xxi.availability.XxiAvailability;
import ru.inversion.msmev.xxi.availability.XxiAvailabilityService;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.U;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class XxiRepositoryExecutor {

   private final ObjectFactory<TaskContext> tcFactory;

   private final XxiAvailabilityService availabilityService;

   /** */
   public <T> T execute( String operation, Map<String, Object> parameters, XxiDbWork<T> work )
   {
      try( XxlLog.Scope ignored = XxlLog.module( XxlLog.Module.DB ) )
      {
         ensureAvailable( operation, parameters );

         try( TaskContext tc = tcFactory.getObject() ) {
              return work.execute(tc);
         } catch (Exception exception) {
            throw normalize(operation, parameters, exception);
         }
      }
   }

   /** */
   public void executeVoid( String operation, Map<String, Object> parameters, XxiDbVoidWork work )
   {
      execute( operation, parameters,tc -> { work.execute(tc); return null;} );
   }

   /** */
   private void ensureAvailable( String operation, Map<String, Object> parameters )
   {
      XxiAvailability availability = availabilityService.currentState();

      /*
       * Если TECHNICAL_BREAK уже достоверно известен.
       * В БД с бизнес-запросом не идём.
       */
      if( availability.technicalBreak() )
          throw technicalBreak( operation, parameters, availability, null );

      /*
       * После CONNECTION_FAILURE, CHECK_FAILURE или при UNKNOWN
       * выполняем проверку. Она не должна запускать десятки одинаковых
       * JDBC-запросов одновременно.
       */
      if( !availability.available() )
           availability = availabilityService.refreshIfUnavailable();

      if( availability.available() )
          return;

      if( availability.technicalBreak() )
          throw technicalBreak( operation, parameters, availability, null );

      throw Errors.dbError (
         "XXI is unavailable before operation: " + operation,
         null,
         Attrs.merge( parameters, availability.parameters(), U.toMap("operation", operation) )
      );
   }


   /** */
   private RuntimeException normalize( String operation, Map<String, Object> parameters, Throwable failure )
   {
      /*
       * Бизнес-ошибки и уже классифицированные payload/config
       * ошибки не перекрашиваем.
       */
      if( !availabilityService.isConnectionFailure(failure))
      {
         if( failure instanceof XXLException exception)
             return exception;

         return Errors.dbError(
           "XXI operation failed: " + operation,
           failure,
           Attrs.merge(
             parameters,
             U.toMap("operation", operation)
           )
         );
      }

      /*
       * Соединение отстрелено. Проверяем marker новым JDBC-вызовом.
       */
      XxiAvailability availability = availabilityService.refreshAfterFailure(failure);

      if( availability.technicalBreak() )
          return technicalBreak( operation, parameters, availability, failure);

      return Errors.dbError(
        "XXI connection failure: " + operation,
        failure,
        Attrs.merge(
          parameters,
          availability.parameters(),
          U.toMap("operation", operation)
        )
      );
   }

   /** */
   private XXLException technicalBreak (
      String operation,
      Map<String, Object> parameters,
      XxiAvailability availability,
      Throwable cause
   )
   {
      return Errors.technicalBreak(
        "XXI is in TECHNICAL_BREAK mode",
        cause,
        Attrs.merge( parameters, availability.parameters(), U.toMap("operation", operation) )
      );
   }

   @FunctionalInterface
   public interface XxiDbWork<T> {
      T execute(TaskContext tc) throws Exception;
   }

   @FunctionalInterface
   public interface XxiDbVoidWork {
      void execute(TaskContext tc) throws Exception;
   }
}