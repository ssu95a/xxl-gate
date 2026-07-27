package ru.inversion.msmev.mi.internal;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.error.XXLException;

import java.util.Map;

/**
 * HTTP error boundary для InternalEndpoint.
 *
 * Handler-ы ничего не знают про HTTP.
 */
@RestControllerAdvice(assignableTypes = InternalEndpoint.class)
@Slf4j
public final class InternalExceptionHandler
{
   /**
    * Ожидаемая нормализованная ошибка XXL.
    */
   @ExceptionHandler(XXLException.class)
   public ResponseEntity<InternalResult> handle( XXLException exception )
   {
      log(exception);

      InternalResult result = InternalResult.error( exception.getResultCode(), exception.getMessage() );

      return ResponseEntity.status(status(exception)).body(result);
   }

   /**
    * Ошибка, которая не была нормализована ниже.
    */
   @ExceptionHandler(Exception.class)
   public ResponseEntity<InternalResult> handleUnexpected( Exception exception )
   {
      log.error(
              "Internal endpoint processing failed: failureClass={}, message={}",
              exception.getClass().getName(),
              exception.getMessage(),
              exception
      );

      InternalResult result = InternalResult.error( Errors.ResultCode.XXL_INTERNAL_ERROR, "Internal XXL query processing error", Map.of() );

      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
   }

   @ExceptionHandler(HttpMessageNotReadableException.class)
   public ResponseEntity<InternalResult> handleBadJson( HttpMessageNotReadableException exception )
   {
      log.warn( "Internal request payload is invalid" );

      return ResponseEntity
              .badRequest()
              .body( InternalResult.error ( "BAD_REQUEST", "Invalid request payload", Map.of() ) );
   }

   /** */
   private static HttpStatus status( XXLException exception )
   {
      return switch( exception.getResultCode() )
      {
         case Errors.ResultCode.MI_SERVICE_BAD_FORMAT,
              Errors.ResultCode.MI_SERVICE_UNSUPPORTED_REQUEST ->
                 HttpStatus.BAD_REQUEST;
         default ->
                 HttpStatus.INTERNAL_SERVER_ERROR;
      };
   }

   /** */
   private static void log( XXLException exception )
   {
      if( exception.getLogPolicy() == Errors.LogPolicy.WARN_NO_STACK )
      {
         log.warn(
                 "Internal request failed: namespace={}, resultCode={}, message={}, attributes={}",
                 exception.getNamespace(),
                 exception.getResultCode(),
                 exception.getMessage(),
                 exception.getAttributes()
         );

         return;
      }

      log.error(
              "Internal request failed: namespace={}, resultCode={}, message={}, attributes={}",
              exception.getNamespace(),
              exception.getResultCode(),
              exception.getMessage(),
              exception.getAttributes(),
              exception
      );
   }
}