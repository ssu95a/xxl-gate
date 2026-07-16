package ru.inversion.msmev.mi.business;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.mi.response.item.MiItemApplyResult;
import ru.inversion.msmev.xxi.repo.XxiRepositoryExecutor;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.U;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractMiBusinessRepository implements MiBusinessRepository {

   private final XxiRepositoryExecutor db;
   private final ObjectMapper objectMapper;

   protected AbstractMiBusinessRepository(XxiRepositoryExecutor db, ObjectMapper objectMapper) {
      this.db = db;
      this.objectMapper = objectMapper;
   }

   /** def.xml конкретного MI_XXXX модуля. */
   protected abstract URL defXml();

   /** Имя операции для diagnostics / db.execute. */
   protected abstract String operationName();

   /** Имя call в def.xml. */
   protected String callName( )
   {
      return "apply_Request";
   }

   /** */
   public MiBusinessResponse apply( MiBusinessRequest request )
   {
      Map<String, Object> parameters = prepareParameters(request);

      return db.execute(
              operationName(),
              parameters,
              tc -> {
                 MiBusinessResponse result = applyRequest( tc, parameters );

                 tc.commit();
                 return result;
              }
      );
   }


   /** */
   protected Map<String, Object> prepareParameters( MiBusinessRequest request )
   {
      //boolean success = true;

      final Map<String, Object> parameters = new LinkedHashMap<>();

      parameters.put( "correlation_id",request.correlationId());
      parameters.put( "message_uuid" , request.messageId()    );
      parameters.put( "request_time" , request.createdAt()    );

      parameters.put( "payload_text", readPayloadText(request));

      //customizeParameters(parameters, response, item, index, success);
      return parameters;
   }


   /** */
   protected String readPayloadText( MiBusinessRequest request )
   {
      MediaType mediaType;

      try {
         mediaType = MediaType.parseMediaType( request.payload().contentType());
      }
      catch (InvalidMediaTypeException exception) {
         throw Errors.miBusinessRequestFailed(
                 "Bad MI item payload media type",
                 exception, request.dump()
         );
      }

      if( !MediaType.APPLICATION_JSON.isCompatibleWith(mediaType) )
          throw Errors.miBusinessRequestFailed (
              "Unsupported MI item payload media type",
              null, request.dump()
          );

      String payloadText;

      try( InputStream stream = request.payload().openStream() )
      {
         payloadText = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
      catch (Exception exception) {
         throw Errors.miResponseBadFormat (
           "Error reading MI item payload",
           exception, request.dump()
         );
      }

      //validateJsonPayload(response, item, index, payloadText);

      return payloadText;
   }

   /** */
   private MiBusinessResponse applyRequest( TaskContext tc, Map<String, Object> parameters )
   {
      URL defXml = defXml();

      if( defXml == null )
         throw Errors.config( "APPLY_REQUEST repository def.xml is null", U.toMap("repository", getClass().getName()) );

      final IDataCall callApply =
              SQLCallBuilder.NEW(tc)
                      .url (defXml )
                      .name(callName())
                      .callBackParameters(new ParametersByName() {
                         @Override
                         public Object getParameter(String name) {
                            if (!parameters.containsKey(name)) {
                               throw Errors.config(
                                    "Unexpected APPLY_REQUEST callback parameter",
                                    U.toMap( "repository", getClass().getName(), "parameter", name, "available_parameters", parameters.keySet() )
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

      return new MiBusinessResponse(
              (UUID)parameters.get("message_uuid"),
              Integer.toString(retVal),
              "OK",
              retInfo, null, Map.of()
      );
   }
}
