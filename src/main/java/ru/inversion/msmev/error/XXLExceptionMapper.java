package ru.inversion.msmev.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.inversion.msmev.dto.XXLResponse;
import ru.inversion.utils.U;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class XXLExceptionMapper {

   private final ObjectMapper objectMapper;

   /** */
   static private XXLException normalize( Throwable throwable )
   {
      if( throwable instanceof XXLException exception )
          return exception;

      return Errors.internal(
         "Unexpected XXL error", throwable,
         U.toMap("exception", throwable == null ? null : throwable.getClass().getName() )
      );
   }

   /** */
   public XXLResponse toXXLResponse( Throwable throwable )
   {
      XXLException exception = normalize( throwable );

      logException( exception );

      Throwable root = causeEx(exception);

      Map<String, Object> parameters = new LinkedHashMap<>();
      parameters.put   ( "namespace", exception.getNamespace().name());
      parameters.putAll( exception.getParameters() );

      return XXLResponse.fail()
        .resultCode(exception.getResultCode())
        .resultInfo(exception.getMessage()   )
        .causeCode (causeCode( exception, root ))
        .causeInfo (causeInfo( exception, root ))
        .causeDetails(causeDetails(exception, root))
        .parameters(parameters)
        .build();
   }

   /** */
   private void logException(XXLException exception)
   {
      if( exception.getLogPolicy() == Errors.LogPolicy.WARN_NO_STACK )
      {
         log.warn (
              "XXL failure: namespace={}, resultCode={}, message={}, cause={}, params={}",
              exception.getNamespace(),
              exception.getResultCode(),
              exception.getMessage(),
              shortCause(exception),
              exception.getParameters()
         );
         return;
      }

      log.error (
           "XXL failure: namespace={}, resultCode={}, message={}, params={}",
           exception.getNamespace(),
           exception.getResultCode(),
           exception.getMessage(),
           exception.getParameters(),
           exception
      );
   }

   /** */
   private String causeCode( XXLException exception, Throwable root ) {
      if( exception.getCause() == null )
          return null;
      return root == null ? null : root.getClass().getSimpleName();
   }

   /** */
   private String causeInfo( XXLException exception, Throwable root ) {
      if( exception.getCause() == null )
          return null;

      return root == null ? null : root.getMessage();
   }

   /** */
   private String causeDetails( XXLException exception, Throwable root )
   {

      if( exception.getCause() == null )
          return null;

      // Не отдаём stack trace в XXLResponse.
      return toJsonSafe (
         U.toMap (
            "root_cause_class",   root == null ? null : root.getClass().getName(),
            "root_cause_message", root == null ? null : root.getMessage()
         )
      );
   }

   /** */
   private Throwable causeEx(Throwable throwable )
   {
      if( throwable == null )
          return null;

      Throwable current = throwable;

      while (current.getCause() != null) {
         current = current.getCause();
      }

      return current;
   }

   /** */
   private String shortCause(Throwable throwable) {

      Throwable root = causeEx(throwable);

      if (root == null) {
         return null;
      }

      String message = root.getMessage();

      if( message == null || message.isBlank() )
         return root.getClass().getSimpleName();


      return root.getClass().getSimpleName() + ": " + message;
   }

   /** */
   private String toJsonSafe( Map<String, Object> value )
   {
      try {
         return objectMapper.writeValueAsString(value);
      } catch( JsonProcessingException e ) {
         return String.valueOf(value);
      }
   }
}