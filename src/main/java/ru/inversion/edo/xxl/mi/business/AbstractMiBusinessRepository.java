package ru.inversion.edo.xxl.mi.business;


import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.U;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractMiBusinessRepository implements MiBusinessRepository
{
   protected final XxiRepositoryExecutor db;

   public AbstractMiBusinessRepository( XxiRepositoryExecutor db )
   {
      this.db = db;
   }

   /** def.xml конкретного MI_XXXX модуля. */
   protected abstract URL defXml();

   /** Имя операции для diagnostics / db.execute. */
   protected abstract String operationName();

   /** Имя call в def.xml. */
   protected String callName()
   {
      return "apply_Request";
   }

   @Override
   public MiBusinessResult apply(MiBusinessRequest request )
   {
      Map<String, Object> parameters = prepareParameters(request);
      Map<String, Object> diagnosticParameters = new LinkedHashMap<>(parameters);
      diagnosticParameters.remove("payload_text");

      return db.execute(
              operationName(),
              diagnosticParameters,
              tc -> {
                 configureTransaction(tc);
                 MiBusinessResult result = applyRequest(tc, parameters);
                 tc.commit();
                 return result;
              }
      );
   }

   /** Настройка транзакции на соединении, выделенном для этого вызова. */
   protected void configureTransaction(TaskContext tc)
   {
   }

   /** */
   protected Map<String, Object> prepareParameters( MiBusinessRequest request )
   {
      Map<String, Object> parameters = new LinkedHashMap<>();

      parameters.put("message_uuid",          request.messageId());
      parameters.put("original_request_uuid", request.requestId());
      parameters.put("correlation_id",        U.nvl( request.correlationId(), UUID.randomUUID() ));
      parameters.put("request_time",          request.createdAt());
      parameters.put("payload_text",          readPayloadText(request));

      return parameters;
   }


   /** Преобразование входного значения только для JDBC-вызова. */
   protected Object bindInput(Object value)
   {
      return value;
   }

   /** */
   private Map<String, Object> responseAttributes( Map<String, Object> parameters )
   {
      Map<String, Object> attrs = new LinkedHashMap<>();

      attrs.put("message_uuid", parameters.get("message_uuid"));
      attrs.put("original_request_uuid", parameters.get("original_request_uuid"));
      attrs.put("correlation_id", parameters.get("correlation_id"));
      attrs.put("request_time", parameters.get("request_time"));

      return attrs;
   }

   /** */
   protected String readPayloadText( MiBusinessRequest request )
   {
      if( request == null )
          throw Errors.miBusinessPayloadBadFormat( "MI business request is null", Map.of() );

      if( request.payload() == null )
          throw Errors.miBusinessPayloadBadFormat( "MI business payload is null", request.dump() );

      String contentType = request.payload().contentType();

      if( contentType == null || contentType.isBlank() )
          throw Errors.miBusinessPayloadBadFormat( "MI business payload contentType is empty", request.dump() );

      MediaType mediaType;

      try
      {
         mediaType = MediaType.parseMediaType(contentType);
      }
      catch( InvalidMediaTypeException exception ) {
         throw Errors.miBusinessPayloadBadFormat( "Bad MI business payload media type", request.dump() );
      }

      if( !MediaType.APPLICATION_JSON.isCompatibleWith(mediaType) )
         throw Errors.miBusinessPayloadBadFormat(
                 "Unsupported MI business payload media type",
                 request.dump()
         );

      try( InputStream stream = request.payload().openStream() )
      {
         return new String( stream.readAllBytes(), StandardCharsets.UTF_8 );
      }
      catch( Exception exception )
      {
         throw Errors.miBusinessRequestFailed (
              "Error reading MI business payload",
              exception, request.dump()
         );
      }
   }


   /** */
   private MiBusinessResult applyRequest(TaskContext tc, Map<String, Object> parameters )
   {
      final URL defXml = defXml();

      if( defXml == null )
          throw Errors.config( "APPLY_REQUEST repository def.xml is null", U.toMap( "repository", getClass().getName() ) );

      final IDataCall callApply =
              SQLCallBuilder.NEW(tc)
                      .url ( defXml )
                      .name( callName() )
                      .callBackParameters( new ParametersByName()
                      {
                         @Override
                         public Object getParameter( String name )
                         {
                            if( !parameters.containsKey(name) )
                               throw Errors.config (
                                    "Unexpected APPLY_REQUEST callback parameter",
                                    U.toMap(
                                      "repository", getClass().getName(),
                                      "parameter",  name,
                                      "available_parameters", parameters.keySet()
                                    )
                               );

                            return bindInput(parameters.get(name));
                         }
                      })
               .build()
            .execute();

      Integer retVal = callApply.getReturnValue();

      String retInfo = callApply.get("ret_info");

      UUID originalRequestUuid = (UUID) parameters.get("original_request_uuid");

      Map<String, Object> attrs = responseAttributes(parameters);


      if(retVal != null && retVal == 0)
      {
         return MiBusinessResult.success(
                 originalRequestUuid,
                 MiBusinessResult.CODE_SUCCESS,
                 retInfo,
                 null,
                 attrs
         );
      }
      
      return MiBusinessResult.error(
         originalRequestUuid,
         retVal == null ? Errors.ResultCode.XXI_CALL_FAILED : Integer.toString(retVal),
         retInfo, attrs
      );
   }
}
