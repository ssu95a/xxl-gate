package ru.inversion.msmev.mi.response;

/**
 * Исключение для retry/nack обработки входящего async-response.
 *
 * Зона ответственности:
 * - дать listener'у способ сигнализировать transport-слою,
 *   что сообщение можно повторить.
 *
 * Важно:
 * - бросать только если точно известно, что transport поддерживает retry/nack/DLQ;
 * - terminal ошибки этим exception не бросать.
 */
public class MiAsyncResponseRetryException extends RuntimeException {

   private final ProcessResult result;

   public MiAsyncResponseRetryException(ProcessResult result) {
      super(result == null ? null : result.resultInfo());
      this.result = result;
   }

   public ProcessResult getResult() {
      return result;
   }
}