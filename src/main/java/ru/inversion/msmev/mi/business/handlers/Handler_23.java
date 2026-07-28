package ru.inversion.msmev.mi.business.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.mi.business.MiBusinessPayload;
import ru.inversion.msmev.mi.business.MiBusinessRequest;
import ru.inversion.msmev.mi.business.MiBusinessRequestHandler;
import ru.inversion.msmev.mi.business.MiBusinessResponse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipInputStream;

@Component
@Slf4j
public class Handler_23 implements MiBusinessRequestHandler {

   @Override
   public Set<Integer> infIds() {
      return Set.of(23);
   }

   @Override
   public MiBusinessResponse handle( MiBusinessRequest request ) {
      log.info( "Save file-to: {}", savePayloadFile( request ) );
      return null;
   }

   private Path savePayloadFile( MiBusinessRequest request ) {

      try {

         final MiBusinessPayload payload = request.payload();

         if( payload == null )
             throw Errors.miBusinessPayloadBadFormat("MI business payload is null", request.dump());

         if(!MediaType.APPLICATION_OCTET_STREAM.isCompatibleWith(payload.mediaType() ) )
             throw Errors.miBusinessPayloadBadFormat("MI business payload is bad mediaType: " + payload.mediaType(), request.dump());

         final Path fileTo = Files.createTempFile( "rci", ".zip" );

         try( final ZipInputStream zis = new ZipInputStream(payload.openStream()) ) {
              Files.copy(zis, fileTo);
         }

         return fileTo;
      }
      catch ( IOException e ) {
         throw new UncheckedIOException(e);
      }
   }
}
