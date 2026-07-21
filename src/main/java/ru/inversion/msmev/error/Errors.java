package ru.inversion.msmev.error;

import ru.inversion.msmev.error.XXLException.Namespace;
import ru.inversion.msmev.util.Attrs;
import ru.inversion.utils.U;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class Errors
{
   private Errors()
   {
   }

   public enum LogPolicy
   {
      WARN_NO_STACK,
      ERROR_WITH_STACK
   }

   public static final class ResultCode
   {
      // XXI -> XXL
      public static final String CONTRACT_ERROR = "CONTRACT_ERROR";

      // XXI request / mi_req / item
      public static final String REQUEST_NOT_FOUND = "REQUEST_NOT_FOUND";
      public static final String REQUEST_MISMATCH  = "REQUEST_MISMATCH";
      public static final String SEND_NOT_ALLOWED  = "SEND_NOT_ALLOWED";
      public static final String EMPTY_CONTAINER   = "EMPTY_CONTAINER";

      // XXI API calls
      public static final String XXI_CALL_FAILED = "XXI_CALL_FAILED";

      // Configuration
      public static final String UNSUPPORTED_WSP_ID = "UNSUPPORTED_WSP_ID";
      public static final String CONFIG_ERROR       = "CONFIG_ERROR";

      // Payload / validation
      public static final String PAYLOAD_BUILD_FAILED = "PAYLOAD_BUILD_FAILED";

      // Transport to/from MI
      public static final String MI_PUBLISH_FAILED   = "MI_PUBLISH_FAILED";
      public static final String MI_TRANSPORT_FAILED = "MI_TRANSPORT_FAILED";

      // Контейнер был опубликован в MI, но статус запроса в XXI не был изменён на SENT.
      public static final String MI_PUBLISHED_STATUS_UPDATE_FAILED = "MI_PUBLISHED_STATUS_UPDATE_FAILED";

      // Async response from MI/S
      public static final String MI_RESPONSE_BAD_FORMAT =
              "MI_RESPONSE_BAD_FORMAT";
      public static final String MI_RESPONSE_APPLY_FAILED =
              "MI_RESPONSE_APPLY_FAILED";
      public static final String MI_RESPONSE_ITEM_NOT_FOUND =
              "MI_RESPONSE_ITEM_NOT_FOUND";
      public static final String MI_RESPONSE_REQUEST_NOT_FOUND =
              "MI_RESPONSE_REQUEST_NOT_FOUND";

      // MI service/internal requests
      public static final String MI_SERVICE_BAD_FORMAT =
              "MI_SERVICE_BAD_FORMAT";
      public static final String MI_SERVICE_UNSUPPORTED_REQUEST =
              "MI_SERVICE_UNSUPPORTED_REQUEST";
      public static final String MI_SERVICE_FAILED =
              "MI_SERVICE_FAILED";
      public static final String MI_SERVICE_REPLY_PUBLISH_FAILED =
              "MI_SERVICE_REPLY_PUBLISH_FAILED";

      // Common technical failures
      public static final String TECHNICAL_BREAK    = "TECHNICAL_BREAK";
      public static final String DB_ERROR           = "DB_ERROR";
      public static final String XXL_INTERNAL_ERROR = "XXL_INTERNAL_ERROR";

      private ResultCode()
      { }
   }

   /** Сломался контракт: пришло не то что ждали или не так заполнено или не туда */
   public static XXLException contract( String message )
   {
      return contract(message, null, null);
   }
   public static XXLException contract( String message, Map<String, Object> attributes )
   {
      return contract(message, null, attributes);
   }
   public static XXLException contract( String message, Throwable cause, Map<String, Object> attributes )
   {
      return error( Namespace.XXI_REQUEST, ResultCode.CONTRACT_ERROR, message, cause, LogPolicy.WARN_NO_STACK, attributes );
   }

   // XXI -> xxl: Запрос с Id не найден в XXI mi_req.
   public static XXLException requestNotFound( long reqId )
   {
      return error( Namespace.XXI_REQUEST, ResultCode.REQUEST_NOT_FOUND, "Request not found in XXI: req_id=" + reqId, null, LogPolicy.WARN_NO_STACK, U.toMap( "req_id", reqId ));
   }
   // XXI -> xxl: Запрос с внешним UUID не найден в XXI mi_req./
   public static XXLException requestNotFound( UUID externalUuid )
   {
      return error( Namespace.XXI_REQUEST, ResultCode.REQUEST_NOT_FOUND, "Request not found in XXI: external_uuid=" + externalUuid, null, LogPolicy.WARN_NO_STACK, U.toMap( "external_uuid", externalUuid ) );
   }

   // XXI -> xxl: Параметры запроса, пришедшего через m-bus, не соответствуют параметрам запроса в БД.
   public static XXLException requestMismatch( long reqId, Map<String, Object> attributes )
   {
      return error( Namespace.XXI_REQUEST, ResultCode.REQUEST_MISMATCH,
              "Request attributes do not match XXI objects: req_id=" + reqId,
              null,
              LogPolicy.WARN_NO_STACK,
              Attrs.merge( attributes, U.toMap("req_id", reqId) )
      );
   }

   // XXI -> xxl: Запрос невозможно отправить в MI.
   public static XXLException sendNotAllowed( String message, Map<String, Object> attributes )
   {
      return error( Namespace.XXI_REQUEST, ResultCode.SEND_NOT_ALLOWED, message, null, LogPolicy.WARN_NO_STACK, attributes );
   }

   // XXI -> xxl: Пустой контейнер с бизнес-данными
   public static XXLException emptyPayloadContainer( long reqId, Map<String, Object> attributes )
   {
      return error(
              Namespace.XXI_REQUEST,
              ResultCode.EMPTY_CONTAINER,
              "No payload items found for request: req_id=" + reqId,
              null,
              LogPolicy.WARN_NO_STACK,
              Attrs.merge( attributes, U.toMap("req_id", reqId) )
      );
   }

   // xxl->XXI: Бизнес-ошибка при вызове хранимой процедуры XXI.
   public static XXLException xxiCallFailed( String callName, long reqId, int retCode, String resInfo )
   {
      return xxiCallFailed( callName, reqId, retCode, resInfo, null );
   }

   // xxl->XXI: Бизнес-ошибка при вызове хранимой процедуры XXI.
   public static XXLException xxiCallFailed( String callName, long reqId, int retCode, String resInfo, UUID callUuid )
   {
      return error(
              Namespace.XXI_CALL,
              ResultCode.XXI_CALL_FAILED,
              "XXI API call returned error: " + callName,
              null,
              LogPolicy.WARN_NO_STACK,
              U.toMap( "call_name", callName, "call_uuid", callUuid, "req_id", reqId, "ret_code", retCode, "res_info", resInfo )
      );
   }

   // XXI -> xxl: Нет поддержки wsp
   public static XXLException unsupportedWsp( String message, Map<String, Object> attributes )
   {
      return unsupportedWsp(message, null, attributes);
   }

   // XXI -> xxl: Нет поддержки wsp
   public static XXLException unsupportedWsp( String message, Throwable cause, Map<String, Object> attributes )
   {
      return error( Namespace.XXL_CONFIG, ResultCode.UNSUPPORTED_WSP_ID, message,cause, LogPolicy.ERROR_WITH_STACK, attributes );
   }

   public static XXLException config(
           String message,
           Map<String, Object> attributes
   )
   {
      return config(message, null, attributes);
   }

   public static XXLException config(
           String message,
           Throwable cause,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.XXL_CONFIG,
              ResultCode.CONFIG_ERROR,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              attributes
      );
   }

   public static XXLException dbConfig(
           String message,
           Throwable cause,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.DB_CONFIG,
              ResultCode.CONFIG_ERROR,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              attributes
      );
   }

   public static XXLException payloadBuildFailed(
           String message,
           Throwable cause,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.XXL_PAYLOAD,
              ResultCode.PAYLOAD_BUILD_FAILED,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              attributes
      );
   }

   public static XXLException miPublishFailed(
           String message,
           Throwable cause,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.MI_TRANSPORT_REQUEST,
              ResultCode.MI_PUBLISH_FAILED,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              attributes
      );
   }

   /** @see ResultCode#MI_PUBLISHED_STATUS_UPDATE_FAILED */
   public static XXLException miPublishedStatusUpdateFailed(
           String message,
           Throwable cause,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.XXI_CALL,
              ResultCode.MI_PUBLISHED_STATUS_UPDATE_FAILED,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              attributes
      );
   }

   public static XXLException miTransportFailed(
           String message,
           Throwable cause,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.MI_TRANSPORT_REQUEST,
              ResultCode.MI_TRANSPORT_FAILED,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              attributes
      );
   }

   public static XXLException miTransportResponseFailed(
           String message,
           Throwable cause,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.MI_TRANSPORT_RESPONSE,
              ResultCode.MI_TRANSPORT_FAILED,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              attributes
      );
   }

   public static XXLException miResponseBadFormat( String message, Map<String, Object> attributes )
   {
      return error(
              Namespace.MI_ASYNC_PAYLOAD,
              ResultCode.MI_RESPONSE_BAD_FORMAT,
              message,
              null,
              LogPolicy.WARN_NO_STACK,
              attributes
      );
   }

   /** */
   public static XXLException miResponseBadFormat( String message, Throwable cause, Map<String, Object> attributes )
   {
      return error( Namespace.MI_ASYNC_PAYLOAD, ResultCode.MI_RESPONSE_BAD_FORMAT, message, cause, LogPolicy.WARN_NO_STACK, attributes );
   }

   public static XXLException miResponseValidationFailed(
           String message,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.MI_ASYNC_VALIDATION,
              ResultCode.MI_RESPONSE_BAD_FORMAT,
              message,
              null,
              LogPolicy.WARN_NO_STACK,
              attributes
      );
   }

   public static XXLException miResponseApplyFailed(
           String message,
           Throwable cause,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.MI_ASYNC_CALL,
              ResultCode.MI_RESPONSE_APPLY_FAILED,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              attributes
      );
   }

   public static XXLException miResponseItemNotFound(
           String message,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.MI_ASYNC_RESPONSE,
              ResultCode.MI_RESPONSE_ITEM_NOT_FOUND,
              message,
              null,
              LogPolicy.WARN_NO_STACK,
              attributes
      );
   }

   public static XXLException miResponseRequestNotFound(
           String message,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.MI_ASYNC_RESPONSE,
              ResultCode.MI_RESPONSE_REQUEST_NOT_FOUND,
              message,
              null,
              LogPolicy.WARN_NO_STACK,
              attributes
      );
   }

   public static XXLException miServiceBadFormat(
           String message,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.MI_INTERNAL_REQUEST,
              ResultCode.MI_SERVICE_BAD_FORMAT,
              message,
              null,
              LogPolicy.WARN_NO_STACK,
              attributes
      );
   }

   public static XXLException miServicePayloadBadFormat(
           String message,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.MI_INTERNAL_PAYLOAD,
              ResultCode.MI_SERVICE_BAD_FORMAT,
              message,
              null,
              LogPolicy.WARN_NO_STACK,
              attributes
      );
   }

   public static XXLException miServiceValidationFailed(
           String message,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.MI_INTERNAL_VALIDATION,
              ResultCode.MI_SERVICE_BAD_FORMAT,
              message,
              null,
              LogPolicy.WARN_NO_STACK,
              attributes
      );
   }

   public static XXLException miServiceUnsupportedRequest(
           String message,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.MI_INTERNAL_CALL,
              ResultCode.MI_SERVICE_UNSUPPORTED_REQUEST,
              message,
              null,
              LogPolicy.WARN_NO_STACK,
              attributes
      );
   }

   public static XXLException miServiceFailed(
           String message,
           Throwable cause,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.MI_INTERNAL_CALL,
              ResultCode.MI_SERVICE_FAILED,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              attributes
      );
   }

   public static XXLException miServicePayloadReadFailed(
           String message,
           Throwable cause,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.MI_INTERNAL_PAYLOAD,
              ResultCode.TECHNICAL_BREAK,
              message,
              cause,
              LogPolicy.WARN_NO_STACK,
              attributes
      );
   }

   public static XXLException miServiceReplyPublishFailed(
           String message,
           Throwable cause,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.MI_TRANSPORT_RESPONSE,
              ResultCode.MI_SERVICE_REPLY_PUBLISH_FAILED,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              attributes
      );
   }

   public static XXLException miBusinessRequestFailed(
           String message,
           Throwable cause,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.MI_BUSINESS_REQUEST,
              ResultCode.TECHNICAL_BREAK,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              attributes
      );
   }

   public static XXLException miBusinessPayloadBadFormat(
           String message,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.MI_BUSINESS_PAYLOAD,
              ResultCode.CONTRACT_ERROR,
              message,
              null,
              LogPolicy.WARN_NO_STACK,
              attributes
      );
   }

   public static XXLException miBusinessCallFailed(
           String message,
           Throwable cause,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.MI_BUSINESS_CALL,
              ResultCode.XXI_CALL_FAILED,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              attributes
      );
   }

   /** */
   public static XXLException dbError( String message, Throwable cause, Map<String, Object> attributes )
   {
      return error (
           Namespace.DB_CALL,
           ResultCode.DB_ERROR,
           message,
           cause,
           LogPolicy.ERROR_WITH_STACK,
           attributes
      );
   }

   public static XXLException internal(
           String message,
           Throwable cause,
           Map<String, Object> attributes
   )
   {
      return error(
              Namespace.INTERNAL,
              ResultCode.XXL_INTERNAL_ERROR,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              attributes
      );
   }

   public static XXLException technicalBreak(
           String message,
           Throwable cause,
           Map<String, Object> attributes
   )
   {
      return technicalBreak(
              Namespace.INTERNAL,
              message,
              cause,
              attributes
      );
   }

   public static XXLException technicalBreak(
           Namespace namespace,
           String message,
           Throwable cause,
           Map<String, Object> attributes
   )
   {
      return error(
              namespace,
              ResultCode.TECHNICAL_BREAK,
              message,
              cause,
              LogPolicy.WARN_NO_STACK,
              attributes
      );
   }

   private static XXLException error(
           Namespace namespace,
           String resultCode,
           String message,
           Throwable cause,
           LogPolicy logPolicy,
           Map<String, Object> attributes
   )
   {
      return new XXLException(
              namespace,
              resultCode,
              message,
              cause,
              logPolicy,
              safeAttributes(attributes)
      );
   }

   private static Map<String, Object> safeAttributes( Map<String, Object> source )
   {
      Map<String, Object> result = new LinkedHashMap<>();

      if( source == null || source.isEmpty() )
         return result;

      for( Map.Entry<String, Object> entry : source.entrySet() )
      {
         if( entry.getKey() != null && entry.getValue() != null )
             result.put(entry.getKey(), entry.getValue() );
      }

      return result;
   }
}