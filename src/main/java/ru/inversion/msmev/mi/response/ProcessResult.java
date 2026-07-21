package ru.inversion.msmev.mi.response;

import ru.inversion.msmev.util.Attrs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <h5>Внутренний результат обработки сообщения из очереди.</h5>
 */
public record ProcessResult(
     boolean success,
     String resultCode,
     String resultInfo,
     boolean shouldRetry,
     Map<String, Object> parameters
)
{
   public ProcessResult {
      parameters = parameters == null || parameters.isEmpty() ? Map.of() : Collections.unmodifiableMap( new LinkedHashMap<>(parameters) );
   }

   public static ProcessResult success(String resultCode, String resultInfo, Map<String, Object> parameters) {
      return new ProcessResult(true, resultCode, resultInfo, false, parameters);
   }

   public static ProcessResult success(String resultCode, String resultInfo, Map<String, Object> ... maps ) {
      return new ProcessResult(true, resultCode, resultInfo, false, Attrs.merge(maps) );
   }

   public static ProcessResult terminal(String resultCode, String resultInfo, Map<String, Object> parameters) {
      return new ProcessResult(false, resultCode, resultInfo, false, parameters);
   }

   public static ProcessResult retryable(String resultCode, String resultInfo, Map<String, Object> parameters) {
      return new ProcessResult(false, resultCode, resultInfo, true, parameters);
   }
}