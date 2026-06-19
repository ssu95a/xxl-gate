package ru.inversion.msmev.mi.response;

/**
 * <h5>Тип входящего async-сообщения ответа из mi-edo.responses.</h5>
 * <p>
 * Зона ответственности:
 * - отделить ответ по отдельному item от полного request reject.
 */
public enum MiAsyncResponseKind {

   /** Результат обработки отдельного item, положительный или отрицательный. */
   ITEM_RESULT,

   /** Весь запрос отклонён по бизнес-причине. */
   REQUEST_REJECTED,

   /** Весь запрос не обработан из-за технической ошибки. */
   REQUEST_FAILED
}