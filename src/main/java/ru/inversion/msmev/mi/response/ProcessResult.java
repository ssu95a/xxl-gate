package ru.inversion.msmev.mi.response;

import ru.inversion.msmev.error.Errors;

import java.util.Map;

/**
 * Внутренний результат обработки сообщения из очереди.
 *
 * Не является внешним контрактом.
 */
public record ProcessResult(
        boolean success,
        String resultCode,
        String resultInfo,
        boolean shouldRetry,
        Map<String, Object> parameters
) {
   public ProcessResult {
      parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
   }

   public static ProcessResult success(String resultCode, String resultInfo, Map<String, Object> parameters) {
      return new ProcessResult(true, resultCode, resultInfo, false, parameters);
   }

   public static ProcessResult success(String resultCode, String resultInfo, Map<String, Object> ... maps ) {
      return new ProcessResult(true, resultCode, resultInfo, false, Errors.merge(maps) );
   }

   public static ProcessResult terminal(String resultCode, String resultInfo, Map<String, Object> parameters) {
      return new ProcessResult(false, resultCode, resultInfo, false, parameters);
   }

   public static ProcessResult retryable(String resultCode, String resultInfo, Map<String, Object> parameters) {
      return new ProcessResult(false, resultCode, resultInfo, true, parameters);
   }
}