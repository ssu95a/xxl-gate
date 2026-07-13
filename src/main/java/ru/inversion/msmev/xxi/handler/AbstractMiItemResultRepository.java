package ru.inversion.msmev.xxi.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
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
import ru.inversion.utils.U;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public abstract class AbstractMiItemResultRepository implements MiItemResultRepository
{
   private static final String RESPONSE_CATEGORY_OK = "OK";

   private static final int RESPONSE_KIND_OK = 0;
   private static final int RESPONSE_KIND_FAIL = -1;

   private final XxiRepositoryExecutor db;
   private final ObjectMapper objectMapper;

   /** */
   protected AbstractMiItemResultRepository( XxiRepositoryExecutor db, ObjectMapper objectMapper )
   {
      this.db = Objects.requireNonNull( db, "db");
      this.objectMapper = Objects.requireNonNull( objectMapper, "objectMapper");
   }

   /** def.xml конкретного MI_XXXX модуля. */
   protected abstract URL defXml();

   /** Имя операции для diagnostics / db.execute. */
   protected abstract String operationName();

   /** Имя call в def.xml. */
   protected String callName( )
   {
      return "apply_Item_Result";
   }

   @Override
   public MiItemApplyResult applyItem( MiAsyncResponse response, MiAsyncItemResult item, int itemIndex )
   {
      Map<String, Object> parameters = prepareParameters(response, item, itemIndex);

      return db.execute(
              operationName(),
              parameters,
              tc -> {
                 MiItemApplyResult result =
                         applyItemImpl(
                                 tc,
                                 parameters,
                                 item.itemExternalUuid(),
                                 itemIndex
                         );

                 tc.commit();
                 return result;
              }
      );
   }

   /**
    * Готовит параметры для унифицированной ХП:
    *
    * request_uuid
    * message_uuid
    * item_uuid
    * response_code
    * response_info
    * response_time
    * payload_text
    */
   protected Map<String, Object> prepareParameters( MiAsyncResponse response, MiAsyncItemResult item, int index )
   {
      boolean success = isSuccessfulItem(item);

      Map<String, Object> parameters = new LinkedHashMap<>();

      parameters.put("request_uuid", response.originalRequestId());
      parameters.put("message_uuid", response.messageId());
      parameters.put("item_uuid", item.itemExternalUuid());

      parameters.put("response_kind", success ? RESPONSE_KIND_OK : RESPONSE_KIND_FAIL);

      parameters.put("response_code", success ? null : item.responseCode());
      parameters.put("response_info", success ? null : item.responseInfo());
      parameters.put("response_details", success ? null : item.responseDetails());
      parameters.put("response_time", item.occurredAt());

      parameters.put(
              "payload_text",
              success
                      ? readRequiredPayloadText(response, item, index)
                      : readForbiddenPayloadText(response, item, index)
      );
      customizeParameters(parameters, response, item, index, success);

      return parameters;
   }

   /**
    * Hook на будущее.
    *
    * По умолчанию всем MI_XXXX хватает единого контракта.
    * Если конкретному модулю понадобится дополнительный callback parameter,
    * можно добавить его здесь в наследнике.
    */
   protected void customizeParameters(
           Map<String, Object> parameters,
           MiAsyncResponse response,
           MiAsyncItemResult item,
           int index,
           boolean success
   )
   {
      // default: no custom parameters
   }

   protected boolean isSuccessfulItem(MiAsyncItemResult item)
   {
      String category = item.responseCategory();
      return category != null && RESPONSE_CATEGORY_OK.equalsIgnoreCase(category.trim());
   }

   protected String readRequiredPayloadText( MiAsyncResponse response, MiAsyncItemResult item, int index )
   {
      if (item.payload() == null) {
         throw Errors.miResponseBadFormat(
                 "MI item successful payload is null",
                 response.itemParameters(item, index)
         );
      }

      return readPayloadText(response, item, index);
   }

   protected String readForbiddenPayloadText(
           MiAsyncResponse response,
           MiAsyncItemResult item,
           int index
   )
   {
      if (item.payload() != null) {
         throw Errors.miResponseBadFormat(
                 "MI item failed payload must be null",
                 response.itemParameters(item, index)
         );
      }

      return null;
   }

   protected String readPayloadText(
           MiAsyncResponse response,
           MiAsyncItemResult item,
           int index
   )
   {
      MediaType mediaType;

      try {
         mediaType =
                 MediaType.parseMediaType(item.payload().contentType());
      }
      catch (InvalidMediaTypeException exception) {
         throw Errors.miResponseBadFormat(
                 "Bad MI item payload media type",
                 exception,
                 response.itemParameters(item, index)
         );
      }

      if (!MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)) {
         throw Errors.miResponseBadFormat(
                 "Unsupported MI item payload media type",
                 response.itemParameters(item, index)
         );
      }

      String payloadText;

      try (InputStream stream = item.payload().openStream()) {
         payloadText =
                 new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
      catch (Exception exception) {
         throw Errors.miResponseBadFormat(
                 "Error reading MI item payload",
                 exception,
                 response.itemParameters(item, index)
         );
      }

      validateJsonPayload(response, item, index, payloadText);

      return payloadText;
   }

   /** */
   protected void validateJsonPayload( MiAsyncResponse response, MiAsyncItemResult item, int index, String payloadText )
   {
      if( payloadText == null || payloadText.isBlank() )
          throw Errors.miResponseBadFormat( "MI item JSON payload is empty", response.itemParameters(item, index) );

      try {
         objectMapper.readTree(payloadText);
      }
      catch (Exception exception) {
         throw Errors.miResponseBadFormat( "MI item JSON payload is invalid", exception, response.itemParameters(item, index) );
      }
   }

   private MiItemApplyResult applyItemImpl(
           TaskContext tc,
           Map<String, Object> parameters,
           UUID itemExternalUuid,
           int itemIndex
   )
   {
      URL defXml = defXml();

      if( defXml == null )
          throw Errors.config( "ITEM_RESULT repository def.xml is null", U.toMap("repository", getClass().getName()) );


      final IDataCall callApply =
              SQLCallBuilder.NEW(tc)
                      .url (defXml )
                      .name(callName())
                      .callBackParameters(new ParametersByName() {
                         @Override
                         public Object getParameter(String name) {
                            if (!parameters.containsKey(name)) {
                               throw Errors.config(
                                       "Unexpected ITEM_RESULT callback parameter",
                                       U.toMap(
                                               "repository",
                                               getClass().getName(),
                                               "parameter",
                                               name,
                                               "available_parameters",
                                               parameters.keySet()
                                       )
                               );
                            }

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