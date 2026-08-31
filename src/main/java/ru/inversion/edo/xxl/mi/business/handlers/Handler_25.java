package ru.inversion.edo.xxl.mi.business.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.mi.business.MiBusinessPayload;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequest;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequestHandler;
import ru.inversion.edo.xxl.mi.business.MiBusinessResult;
import ru.inversion.utils.S;
import ru.inversion.utils.dco.Dco;
import ru.inversion.utils.dco.IDco;

import java.util.Map;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class Handler_25 implements MiBusinessRequestHandler {

   private final Repository_25 repository;

   @Override
   public Set<Integer> infIds() {
      return Set.of(25);
   }

   /** */
   @Override
   public MiBusinessResult handle( MiBusinessRequest request)
   {
      validatePayload( request );
      validateHeaders( request );

      return repository.apply( request );
   }

   /** */
   private void validatePayload( MiBusinessRequest request )
   {
      MiBusinessPayload payload = request.payload();

      if( payload == null )
          throw Errors.miBusinessPayloadBadFormat( "MI business payload is null", request.dump() );

      if( !MediaType.APPLICATION_OCTET_STREAM.isCompatibleWith( payload.mediaType() ) )
          throw Errors.miBusinessPayloadBadFormat( "MI business payload has unsupported mediaType: " + payload.mediaType(), request.dump() );

   }

   private void validateHeaders( MiBusinessRequest request )
   {
      final Map<String, Object> headers = request.headers();

      if( headers == null || headers.isEmpty() )
          throw Errors.miBusinessPayloadBadFormat( "MI business headers is null or empty", request.dump() );

      Object s = headers.get("businessPayload");
      if( s == null )
          throw Errors.miBusinessPayloadBadFormat( "MI business headers value 'businessPayload' is null", request.dump() );

      if( !S.isString(s)  )
          throw Errors.miBusinessPayloadBadFormat( "MI business headers value 'businessPayload' is not String type", request.dump() );

      final String bp = s.toString();

      try {
         IDco json = Dco.parseJson("businessPayload",bp);
      } catch( Exception e ) {
         throw Errors.miBusinessPayloadBadFormat( "MI business headers value 'businessPayload' is not valid JSON content", e, request.dump() );
      }
   }
}
