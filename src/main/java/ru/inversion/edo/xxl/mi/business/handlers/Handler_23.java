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

import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class Handler_23 implements MiBusinessRequestHandler {

   private final Repository_23 repository;

   @Override
   public Set<Integer> infIds() {
      return Set.of(23);
   }

   /** */
   @Override
   public MiBusinessResult handle(MiBusinessRequest request)
   {
      validatePayload(request);

      return repository.apply(request);
   }

   /** */
   private void validatePayload( MiBusinessRequest request )
   {
      MiBusinessPayload payload = request.payload();

      if( payload == null )
          throw Errors.miBusinessPayloadBadFormat( "MI business payload is null", request.dump() );

      if( !MediaType.APPLICATION_OCTET_STREAM.isCompatibleWith(payload.mediaType() ) )
          throw Errors.miBusinessPayloadBadFormat( "MI business payload has unsupported mediaType: " + payload.mediaType(), request.dump() );
   }
}
