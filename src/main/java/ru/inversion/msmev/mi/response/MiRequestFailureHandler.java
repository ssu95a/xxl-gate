package ru.inversion.msmev.mi.response;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.model.MiAsyncResponseKind;
import ru.inversion.msmev.error.Errors;
import ru.inversion.utils.U;

import java.util.Collections;

@Component
@RequiredArgsConstructor
public class MiRequestFailureHandler implements MiAsyncResponseHandler
{
   private final MiRequestFailureDispatcher dispatcher;

   /** */
   @Override
   public boolean supports(MiAsyncResponse response)
   {
      if( response == null )
          return false;

      return U.in( response.kind(), MiAsyncResponseKind.REQUEST_REJECTED,  MiAsyncResponseKind.REQUEST_FAILED );
   }

   /** */
   @Override
   public ProcessResult handle(MiAsyncResponse response)
   {
      if( !supports(response) )
      {
         throw Errors.miResponseBadFormat(
                 "MiRequestFailureHandler received unsupported response",
                 response == null ? Collections.emptyMap() : response.parameters()
         );
      }
      return dispatcher.dispatch(response);
   }
}