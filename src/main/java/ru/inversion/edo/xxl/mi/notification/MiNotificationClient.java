package ru.inversion.edo.xxl.mi.notification;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.mi.internal.InternalRequest;
import ru.inversion.edo.xxl.mi.internal.InternalResult;
import ru.inversion.utils.U;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Component
public class MiNotificationClient
{
   private static final String SOURCE_SYSTEM = "XXL-gate";
   private static final String SOURCE_VERSION = "1.0.0";

   private final RestClient restClient;

   public MiNotificationClient( RestClient.Builder builder, @Value("${mi.internal.endpoint}") String endpoint )
   {
      this.restClient = builder.baseUrl(endpoint).build();
   }

   public InternalResult send( String queryType, Map<String, Object> params)
   {
      InternalRequest request = new InternalRequest(
              UUID.randomUUID(),
              queryType,
              params,
              OffsetDateTime.now(),
              SOURCE_SYSTEM,
              SOURCE_VERSION
      );

      InternalResult response =
              restClient.post()
                      .contentType(MediaType.APPLICATION_JSON)
                      .body(request)
                      .retrieve()
                      .body(InternalResult.class);

      validate(queryType, response);

      return response;
   }

   private void validate(String queryType, InternalResult response)
   {
      if( response == null )
      {
         throw Errors.miInternalFailed(
                 "MI notification returned empty response",
                 null,
                 U.toMap("query_type", queryType)
         );
      }

      if( !"SUCCESS".equalsIgnoreCase(response.responseCategory())
              || !"OK".equalsIgnoreCase(response.responseCode()) )
      {
         throw Errors.miInternalFailed(
                 "MI notification failed",
                 null,
                 U.toMap(
                         "query_type", queryType,
                         "response_code", response.responseCode(),
                         "response_category", response.responseCategory(),
                         "response_info", response.responseInfo()
                 )
         );
      }
   }
}