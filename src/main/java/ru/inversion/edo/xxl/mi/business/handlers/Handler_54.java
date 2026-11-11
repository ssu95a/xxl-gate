package ru.inversion.edo.xxl.mi.business.handlers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.mi.business.MiBusinessPayload;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequest;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequestHandler;
import ru.inversion.edo.xxl.mi.business.MiBusinessResult;

import java.util.Set;

/** <h5>Гис-антифрод-получение</h5> */
@Component
@RequiredArgsConstructor
public class Handler_54 implements MiBusinessRequestHandler {

   private final Repository_81 repository;

   @Override
   public Set<Integer> infIds() {
      return Set.of(54);
   }

   /** */
   private void validatePayload( MiBusinessRequest request )
   {
      MiBusinessPayload payload = request.payload();

      if( payload == null )
          throw Errors.miBusinessPayloadBadFormat( "MI business payload is null", request.dump() );

      if(!MediaType.APPLICATION_JSON.isCompatibleWith( payload.mediaType() ) )
          throw Errors.miBusinessPayloadBadFormat( "MI business payload has unsupported mediaType: " + payload.mediaType(), request.dump() );
   }

   /** */
   @Override
   public MiBusinessResult handle(MiBusinessRequest request ) {

      validatePayload( request );

      return repository.apply(request);
   }
}
