package ru.inversion.edo.xxl.error;

import org.slf4j.Logger;

import java.util.Map;

public final class XXLExceptionLogger
{
   private XXLExceptionLogger()
   {
   }

   public static void log(
           Logger log,
           Throwable failure,
           String message,
           Map<String, Object> context
   )
   {
      if( failure instanceof XXLException exception )
      {
         log(log, exception, message, context);
         return;
      }

      log.error(
              "{}: context={}, failureClass={}, message={}",
              message,
              context,
              failure.getClass().getName(),
              failure.getMessage(),
              failure
      );
   }


   public static void log( Logger log, XXLException exception, String message, Map<String, Object> context )
   {
      if( exception.getLogPolicy() == Errors.LogPolicy.WARN_NO_STACK )
      {
         log.warn(
                 "{}: context={}, namespace={}, resultCode={}, message={}",
                 message,
                 context,
                 exception.getNamespace(),
                 exception.getResultCode(),
                 exception.getMessage()
         );

         return;
      }

      log.error(
              "{}: context={}, namespace={}, resultCode={}, message={}",
              message,
              context,
              exception.getNamespace(),
              exception.getResultCode(),
              exception.getMessage(),
              exception
      );
   }

   /** */
   public static void log( Logger log, Throwable failure, String message )
   {
      log( log, failure, message, Map.of() );
   }
}