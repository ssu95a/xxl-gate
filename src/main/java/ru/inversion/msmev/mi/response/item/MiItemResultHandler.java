package ru.inversion.msmev.mi.response.item;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.model.MiAsyncResponseKind;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.mi.response.MiAsyncResponse;
import ru.inversion.msmev.mi.response.MiAsyncResponseHandler;
import ru.inversion.msmev.mi.response.ProcessResult;
import ru.inversion.utils.U;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MiItemResultHandler implements MiAsyncResponseHandler {

   private static final String RESULT_CODE = "MI_ITEM_CONTAINER_APPLIED";

   private final MiItemResultDispatcher dispatcher;

   @Override
   public boolean supports( MiAsyncResponse response )
   {
      return response != null && response.kind() == MiAsyncResponseKind.ITEM_RESULT;
   }

   @Override
   public ProcessResult handle(MiAsyncResponse response)
   {
      if( !supports(response) )
          throw Errors.miResponseBadFormat( "MiItemResultHandler received unsupported response", response == null ? Collections.emptyMap() : response.parameters() );

      dispatcher.dispatch(response);

      return ProcessResult.success( RESULT_CODE, "ITEM_RESULT container applied to XXI", Errors.merge( response.parameters(), U.toMap( "applied_item_count", response.itemCount() ) ) );
   }
}