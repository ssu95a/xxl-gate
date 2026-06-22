package ru.inversion.msmev.xxi.availability;

/**
 * <h6>Текущее состояние доступности XXI для бизнес-вызовов из XXL.</h6>
 * <p>
 * TECHNICAL_BREAK означает, что база может быть доступна,
 * но выполнять бизнес-операции в XXI временно нельзя.
 */
public enum XxiAvailabilityState {

   /** XXI доступна для бизнес-вызовов. */
   AVAILABLE,

   /** В XXI установлен штатный маркер технического перерыва. */
   TECHNICAL_BREAK,

   /** Не удалось установить или сохранить JDBC-соединение. */
   CONNECTION_FAILURE,

   /** Проверка доступности выполнилась с другой технической ошибкой. */
   CHECK_FAILURE,

   /** Проверка ещё не выполнялась или достоверное состояние неизвестно. */
   UNKNOWN;

   public boolean available() {
      return this == AVAILABLE;
   }
}
