package ru.inversion.msmev.mi.response;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.msmev.error.Errors;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MiItemResultHandler
        implements MiAsyncResponseHandler {

   private static final String RESULT_CODE =
           "MI_ITEM_CONTAINER_APPLIED";

   private final MiItemResultDispatcher dispatcher;

   @Override
   public boolean supports(MiAsyncResponse response)
   {
      return response != null
              && response.kind()
              == MiAsyncResponseKind.ITEM_RESULT;
   }

   @Override
   public ProcessResult handle(MiAsyncResponse response)
   {
      requireSupported(response);

      dispatcher.dispatch(response);

      Map<String, Object> parameters =
              new LinkedHashMap<>(
                      response.parameters()
              );

      parameters.put(
              "applied_item_count",
              response.itemCount()
      );

      return ProcessResult.success(
              RESULT_CODE,
              "ITEM_RESULT container applied to XXI",
              parameters
      );
   }

   private void requireSupported(
           MiAsyncResponse response
   ) {
      if (supports(response))
         return;

      throw Errors.miResponseBadFormat(
              "MiItemResultHandler received unsupported response",
              response == null
                      ? Collections.emptyMap()
                      : response.parameters()
      );
   }
}