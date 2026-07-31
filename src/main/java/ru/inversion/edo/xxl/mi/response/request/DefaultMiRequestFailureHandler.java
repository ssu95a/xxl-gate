package ru.inversion.edo.xxl.mi.response.request;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.mi.response.MiAsyncResponse;
import ru.inversion.edo.xxl.mi.response.ProcessResult;
import ru.inversion.edo.xxl.util.Attrs;
import ru.inversion.edo.xxl.xxi.repo.ReqRepository;
import ru.inversion.utils.U;

@Component
@RequiredArgsConstructor
public class DefaultMiRequestFailureHandler {

   private final ReqRepository reqRepository;

   /** */
   public ProcessResult handle( MiAsyncResponse r )
   {
      reqRepository.applyRequestFailure( r.originalRequestId(), r.messageId(), r.kind().name(), r.responseCode(), r.responseInfo(), r.responseDetails(), r.occurredAt() );

      return ProcessResult.success (
         resultCode(r),
         "Request failure applied to XXI",
         Attrs.merge( r.parameters(), U.toMap( "handler", getClass().getSimpleName(), "applied_status", -1 ) )
      );
   }

   /** */
   private String resultCode( MiAsyncResponse response )
   {
      return switch( response.kind() )
      {
         case REQUEST_REJECTED -> "MI_REQUEST_REJECTED_APPLIED";
         case REQUEST_FAILED   -> "MI_REQUEST_FAILED_APPLIED";
         default -> throw new IllegalArgumentException( "Unsupported response kind: " + response.kind() );
      };
   }
}