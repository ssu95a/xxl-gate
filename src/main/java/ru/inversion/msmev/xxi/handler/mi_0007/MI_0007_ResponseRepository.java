package ru.inversion.msmev.xxi.handler.mi_0007;

import lombok.RequiredArgsConstructor;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.mi.transport.model.MiAsyncItemResult;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.mi.response.MiAsyncResponse;
import ru.inversion.msmev.mi.response.item.MiItemApplyResult;
import ru.inversion.msmev.mi.response.item.MiItemResultRepository;
import ru.inversion.msmev.xxi.repo.XxiRepositoryExecutor;
import ru.inversion.tc.TaskContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MI_0007_ResponseRepository implements MiItemResultRepository
{
   private static final String INF_NAMESPACE = "urn://mvd/gismu/RFP_ACTUAL_BANK/1.0.1";

   public static final URL defXml = MI_0007_ResponseRepository.class.getResource("plsql/def.xml");

   private final XxiRepositoryExecutor db;

   private final ObjectMapper objectMapper;

   @Override
   public String infNamespace()
   {
      return INF_NAMESPACE;
   }

   @Override
   public Set<Integer> infIds()
   {
      return Set.of(71, 72, 73, 74, 75);
   }

   @Override
   public MiItemApplyResult applyItem( MiAsyncResponse response, MiAsyncItemResult item, int itemIndex )
   {
      Map<String, Object> parameters = prepareParameters( response, item, itemIndex );

      return db.execute(
              "MI_0007.applyItemResponse",
              response.itemParameters(item, itemIndex),
              tc -> {
                 MiItemApplyResult result = applyItemImpl(tc, parameters, item.itemExternalUuid(), itemIndex);
                 tc.commit();
                 return result;
             }
      );
   }

   /**
    * Готовит параметры для MI_0007_Api.apply_Item_Result.
    * <p>
    * Ключи map должны совпадать с именами callback-параметров в def.xml.
    */
   private Map<String, Object> prepareParameters( MiAsyncResponse response, MiAsyncItemResult item, int index )
   {
      Map<String, Object> parameters = new LinkedHashMap<>();

      boolean success = "OK".equalsIgnoreCase( item.responseCategory() );

      if( !success && item.payload() != null )
          throw Errors.miResponseBadFormat( "MI_0007 failed item must not contain payload", response.itemParameters(item, index) );

      if( success && item.payload() == null )
          throw Errors.miResponseBadFormat( "MI_0007 successful item payload is null", response.itemParameters(item, index) );

      parameters.put("item_uuid",     item.itemExternalUuid() );
      parameters.put("message_uuid",  response.messageId()    );
      parameters.put("request_uuid",  response.originalRequestId() );
      parameters.put("response_code", success ? null : item.responseCode());
      parameters.put("response_info", success ? null : item.responseInfo());
      parameters.put("response_time", item.occurredAt());
      parameters.put("payload_text",  success
              ? readRequiredPayloadText(response, item, index)
              : readForbiddenOrIgnoredPayloadText(response, item, index)
      );

      return parameters;
   }


   /** */
   private String readRequiredPayloadText( MiAsyncResponse response, MiAsyncItemResult item, int index )
   {
      if (item.payload() == null)
         throw Errors.miResponseBadFormat( "MI_0007 successful item payload is null", response.itemParameters(item, index) );

      return readPayloadText(response, item, index);
   }


   private String readForbiddenOrIgnoredPayloadText(
           MiAsyncResponse response,
           MiAsyncItemResult item,
           int index
   )
   {
      if( item.payload() != null )
         throw Errors.miResponseBadFormat (
              "MI_0007 failed item must not contain payload",
              response.itemParameters(item, index)
         );

      return null;
   }

   /** */
   private String readPayloadText( MiAsyncResponse response, MiAsyncItemResult item, int index )
   {

      MediaType mediaType;

      try {
         mediaType = MediaType.parseMediaType(item.payload().contentType());
      }
      catch (InvalidMediaTypeException exception) {
         throw Errors.miResponseBadFormat( "Bad MI_0007 item payload media type", exception, response.itemParameters(item, index) );
      }

      if(!MediaType.APPLICATION_JSON.isCompatibleWith(mediaType))
          throw Errors.miResponseBadFormat( "Unsupported MI_0007 item payload media type", response.itemParameters(item, index) );

      try( InputStream stream = item.payload().openStream() ) {
           return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
      catch( Exception exception ) {
         throw Errors.miResponseBadFormat( "Error reading MI_0007 item JSON payload", exception, response.itemParameters(item, index) );
      }
   }

   /** */
   private void validateJsonPayload( MiAsyncResponse response, MiAsyncItemResult item, int index, String payloadText )
   {
      if( payloadText == null || payloadText.isBlank() )
          throw Errors.miResponseBadFormat( "MI_0007 item JSON payload is empty", response.itemParameters(item, index) );

      try {
         objectMapper.readTree(payloadText);
      }
      catch( Exception exception ) {
         throw Errors.miResponseBadFormat( "MI_0007 item JSON payload is invalid", exception, response.itemParameters(item, index) );
      }
   }


   private MiItemApplyResult applyItemImpl( TaskContext tc, Map<String, Object> parameters, UUID itemExternalUuid, int itemIndex )
   {
      final IDataCall callApply =
             SQLCallBuilder.NEW(tc)
                      .url(defXml)
                      .name("apply_Item_Result")
                      .callBackParameters(new ParametersByName() {
                         @Override
                         public Object getParameter(String name) {
                            if( !parameters.containsKey(name))
                                throw new IllegalStateException( "Unexpected MI_0007 apply parameter: " + name );
                            return parameters.get(name);
                         }
                      })
             .build()
               .execute();

      final int retVal = callApply.<Integer>getReturnValue();
      final String retInfo = callApply.get("ret_info");

      final MiItemApplyResult.Status status = MiItemApplyResult.Status.ofInt(retVal);

      return new MiItemApplyResult(
              itemIndex,
              itemExternalUuid,
              status,
              Integer.toString(retVal),
              retInfo
      );
   }
}