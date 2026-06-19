package ru.inversion.msmev.error;

import ru.inversion.utils.U;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class Errors {

   private Errors() {
   }

   public enum LogPolicy {
      WARN_NO_STACK,
      ERROR_WITH_STACK
   }

   public static final class ResultCode {

      // XXI -> XXL
      public static final String CONTRACT_ERROR = "CONTRACT_ERROR";

      // X objects / mi_req / item
      public static final String REQUEST_NOT_FOUND = "REQUEST_NOT_FOUND";
      public static final String REQUEST_MISMATCH  = "REQUEST_MISMATCH";

      public static final String SEND_NOT_ALLOWED  = "SEND_NOT_ALLOWED";

      public static final String EMPTY_CONTAINER   = "EMPTY_CONTAINER";

      // XXI SP calls error
      public static final String XXI_CALL_FAILED   = "XXI_CALL_FAILED";

      // Configuration
      public static final String UNSUPPORTED_WSP_ID = "UNSUPPORTED_WSP_ID";
      public static final String CONFIG_ERROR = "CONFIG_ERROR";

      // Payload / validation
      public static final String PAYLOAD_BUILD_FAILED = "PAYLOAD_BUILD_FAILED";

      // Transport to MI
      public static final String MI_PUBLISH_FAILED   = "MI_PUBLISH_FAILED";
      public static final String MI_TRANSPORT_FAILED = "MI_TRANSPORT_FAILED";
      public static final String MI_PUBLISHED_STATUS_UPDATE_FAILED ="MI_PUBLISHED_STATUS_UPDATE_FAILED"; // Ошиба когда к MI ушло, а ЦАБС статус не поменялся
                                                                                                         // to_Sent с ошибкой завершилас

      // Async response from MI
      public static final String MI_RESPONSE_BAD_FORMAT         = "MI_RESPONSE_BAD_FORMAT";
      public static final String MI_RESPONSE_APPLY_FAILED       = "MI_RESPONSE_APPLY_FAILED";
      public static final String MI_RESPONSE_ITEM_NOT_FOUND     = "MI_RESPONSE_ITEM_NOT_FOUND";
      public static final String MI_RESPONSE_REQUEST_NOT_FOUND  = "MI_RESPONSE_REQUEST_NOT_FOUND";

      // MI service requests - запрос от MI для получения инфы из XXI
      public static final String MI_SERVICE_BAD_FORMAT = "MI_SERVICE_BAD_FORMAT";
      public static final String MI_SERVICE_UNSUPPORTED_REQUEST = "MI_SERVICE_UNSUPPORTED_REQUEST";
      public static final String MI_SERVICE_FAILED = "MI_SERVICE_FAILED";
      public static final String MI_SERVICE_REPLY_PUBLISH_FAILED = "MI_SERVICE_REPLY_PUBLISH_FAILED";

      // Common technical failures
      public static final String DB_ERROR = "DB_ERROR"; // on SQLException
      public static final String XXL_INTERNAL_ERROR = "XXL_INTERNAL_ERROR";

      private ResultCode() {
      }
   }

   public static XXLException contract(String message) {
      return contract(message, null, null);
   }

   public static XXLException contract(String message, Map<String, Object> params) {
      return contract(message, null, params);
   }

   public static XXLException contract(String message, Throwable cause, Map<String, Object> params) {
      return new XXLException (
              XXLException.Namespace.XXI_REQUEST,
              ResultCode.CONTRACT_ERROR,
              message,
              cause,
              LogPolicy.WARN_NO_STACK,
              params
      );
   }

   /** Запрос с Id не найден в XXL mi_req */
   public static XXLException requestNotFound( long reqId )
   {
      return new XXLException(
              XXLException.Namespace.XXI_OBJECT,
              ResultCode.REQUEST_NOT_FOUND,
              "Request not found in XXI: req_id=" + reqId,
              null,
              LogPolicy.WARN_NO_STACK,
              U.toMap( "req_id", reqId )
      );
   }

   /** Запрос с внешним ID UUID, не найден в XXL mi_req */
   public static XXLException requestNotFound(UUID externalUuid) {
      return new XXLException (
              XXLException.Namespace.XXI_OBJECT,
              ResultCode.REQUEST_NOT_FOUND,
              "Request not found in XXI: external_uuid=" + externalUuid,
              null,
              LogPolicy.WARN_NO_STACK,
              U.toMap("external_uuid", externalUuid)
      );
   }

   /** Параметры запроса пришедшего через гейт, не соотвт. параметрам запроса в БД.
    *  Нужно для контроля транспорта PG -> M-bus
    */
   public static XXLException requestMismatch( long reqId, Map<String, Object> params) {
      return new XXLException(
         XXLException.Namespace.XXI_OBJECT,
         ResultCode.REQUEST_MISMATCH,
         "Request attributes do not match X objects: req_id=" + reqId,
         null,
         LogPolicy.WARN_NO_STACK,
         params
      );
   }

   /** Запрос не возможно отправить в mi, причина в message и params*/
   public static XXLException sendNotAllowed( String message, Map<String, Object> params ) {
      return new XXLException (
         XXLException.Namespace.XXI_OBJECT,
         ResultCode.SEND_NOT_ALLOWED,
         message,
         null,
         LogPolicy.WARN_NO_STACK,
         params
      );
   }

   /** */
   public static XXLException emptyPayloadContainer( long reqId, Map<String, Object> params ) {

      params.put( "req_id", reqId );

      return new XXLException(
         XXLException.Namespace.XXI_OBJECT,
         ResultCode.EMPTY_CONTAINER,
         "No payload items found for request: req_id=" + reqId,
         null,
         LogPolicy.WARN_NO_STACK,
         params
      );
   }

   public static XXLException xxiCallFailed(
           String callName,
           long reqId,
           int retCode,
           String resInfo
   ) {
      return xxiCallFailed(callName, reqId, retCode, resInfo, null);
   }

   public static XXLException xxiCallFailed(
           String callName,
           long reqId,
           int retCode,
           String resInfo,
           UUID callUuid
   ) {
      return new XXLException(
              XXLException.Namespace.XXI_CALL,
              ResultCode.XXI_CALL_FAILED,
              "X API call returned error: " + callName,
              null,
              LogPolicy.WARN_NO_STACK,
              U.toMap (
                   "call_name", callName,
                   "call_uuid", callUuid,
                   "req_id",    reqId,
                   "ret_code",  retCode,
                   "res_info",  resInfo
              )
      );
   }

   public static XXLException unsupportedWsp(String message, Map<String, Object> params) {
      return unsupportedWsp(message, null, params);
   }

   public static XXLException unsupportedWsp(String message, Throwable cause, Map<String, Object> params) {
      return new XXLException(
              XXLException.Namespace.CONFIG,
              ResultCode.UNSUPPORTED_WSP_ID,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              params
      );
   }

   public static XXLException config(String message, Map<String, Object> params) {
      return config(message, null, params);
   }

   public static XXLException config(String message, Throwable cause, Map<String, Object> params) {
      return new XXLException(
              XXLException.Namespace.CONFIG,
              ResultCode.CONFIG_ERROR,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              params
      );
   }

   public static XXLException payloadBuildFailed(String message, Throwable cause, Map<String, Object> params) {
      return new XXLException(
              XXLException.Namespace.PAYLOAD,
              ResultCode.PAYLOAD_BUILD_FAILED,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              params
      );
   }

   public static XXLException miPublishFailed(String message, Throwable cause, Map<String, Object> params) {
      return new XXLException(
              XXLException.Namespace.MI_TRANSPORT,
              ResultCode.MI_PUBLISH_FAILED,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              params
      );
   }

   /** @see ResultCode.MI_PUBLISHED_STATUS_UPDATE_FAILED */
   public static XXLException miPublishedStatusUpdateFailed( String message, Throwable cause, Map<String, Object> params )
   {
      return new XXLException(
              XXLException.Namespace.XXI_CALL,
              ResultCode.MI_PUBLISHED_STATUS_UPDATE_FAILED,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              params
      );
   }

   public static XXLException miTransportFailed(String message, Throwable cause, Map<String, Object> params) {
      return new XXLException(
              XXLException.Namespace.MI_TRANSPORT,
              ResultCode.MI_TRANSPORT_FAILED,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              params
      );
   }

   public static XXLException miResponseBadFormat(String message, Map<String, Object> params) {
      return new XXLException(
              XXLException.Namespace.MI_RESPONSE,
              ResultCode.MI_RESPONSE_BAD_FORMAT,
              message,
              null,
              LogPolicy.WARN_NO_STACK,
              params
      );
   }

   public static XXLException miResponseApplyFailed(String message, Throwable cause, Map<String, Object> params) {
      return new XXLException(
              XXLException.Namespace.MI_RESPONSE,
              ResultCode.MI_RESPONSE_APPLY_FAILED,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              params
      );
   }

   public static XXLException miResponseItemNotFound(String message, Map<String, Object> params) {
      return new XXLException(
              XXLException.Namespace.MI_RESPONSE,
              ResultCode.MI_RESPONSE_ITEM_NOT_FOUND,
              message,
              null,
              LogPolicy.WARN_NO_STACK,
              params
      );
   }

   public static XXLException miResponseRequestNotFound(String message, Map<String, Object> params) {
      return new XXLException(
              XXLException.Namespace.MI_RESPONSE,
              ResultCode.MI_RESPONSE_REQUEST_NOT_FOUND,
              message,
              null,
              LogPolicy.WARN_NO_STACK,
              params
      );
   }

   public static XXLException miServiceBadFormat(String message, Map<String, Object> params) {
      return new XXLException(
              XXLException.Namespace.MI_SERVICE,
              ResultCode.MI_SERVICE_BAD_FORMAT,
              message,
              null,
              LogPolicy.WARN_NO_STACK,
              params
      );
   }

   public static XXLException miServiceFailed(String message, Throwable cause, Map<String, Object> params) {
      return new XXLException(
              XXLException.Namespace.MI_SERVICE,
              ResultCode.MI_SERVICE_FAILED,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              params
      );
   }


   public static XXLException dbError(String message, Throwable cause, Map<String, Object> params) {
      return new XXLException(
              XXLException.Namespace.DB,
              ResultCode.DB_ERROR,
              message,
              cause,
              LogPolicy.ERROR_WITH_STACK,
              params
      );
   }

   /** */
   public static XXLException internal(String message, Throwable cause, Map<String, Object> params) {
      return new XXLException (
         XXLException.Namespace.INTERNAL,
            ResultCode.XXL_INTERNAL_ERROR,
               message,
            cause,
               LogPolicy.ERROR_WITH_STACK,
            params
      );
   }

   /**
    * Объединяет несколько мап в одну новую.
    * <p>
    * - Все переданные мапы копируются в новую.
    * - При совпадении ключей значение из мапы, переданной позже, перезаписывает предыдущее.
    * - Мапы, равные {@code null} или пустые, игнорируются.
    * - Возвращается новая {@link LinkedHashMap}, сохраняющая порядок вставки.
    *
    * @param maps мапы для объединения (может быть {@code null} или пустым массивом)
    * @return новая мапа, содержащая все записи из переданных мап (порядок сохраняется)
    */
   @SafeVarargs
   public static Map<String, Object> merge(Map<String, Object>... maps) {

      Map<String, Object> result = new LinkedHashMap<>();
      if (maps == null || maps.length == 0) {
         return result;
      }

      for(Map<String, Object> map : maps )
      {
         if( map != null && !map.isEmpty() )
             result.putAll(map);
      }

      return result;
   }
}