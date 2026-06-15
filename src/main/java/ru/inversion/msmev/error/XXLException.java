package ru.inversion.msmev.error;

import lombok.EqualsAndHashCode;
import lombok.Value;
import ru.inversion.utils.IExceptionInfo;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Value
public class XXLException extends RuntimeException {

   public enum Namespace {
      XXI_REQUEST,      // входной запрос от XXI: формат, обязательные поля, version/action
      XXI_OBJECT,       // объекты XXI: mi_req, item, req_id, status_cd, identity mismatch
      XXI_CALL,         // управляемая ошибка вызова API XXI: ret_code != 0, res_info

      CONFIG,           // настройки XXL/mi_inf/js/handler/routing

      DB,               // технические ошибки JDBC/DataSet/driver/connection

      PAYLOAD,          // сборка payload/DTCM/JS

      VALIDATION,       // XSD/структурная валидация

      MI_TRANSPORT,     // отправка в MI/Rabbit/transport
      MI_RESPONSE,      // обработка async response от MI/S
      MI_SERVICE,       // сервисные запросы MI -> XXL

      INTERNAL          // сломался XXL
   }

   Namespace namespace;
   String resultCode;
   Errors.LogPolicy logPolicy;
   Map<String, Object> parameters;

   public XXLException(
      Namespace namespace,
      String resultCode,
      String message,
      Throwable cause,
      Errors.LogPolicy logPolicy,
      Map<String, Object> parameters
   )
   {
      super(message, cause);
      this.namespace  = namespace  == null ? Namespace.INTERNAL : namespace;
      this.resultCode = resultCode == null ? Errors.ResultCode.XXL_INTERNAL_ERROR : resultCode;
      this.logPolicy  = logPolicy  == null ? Errors.LogPolicy.ERROR_WITH_STACK : logPolicy;
      this.parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
   }
}