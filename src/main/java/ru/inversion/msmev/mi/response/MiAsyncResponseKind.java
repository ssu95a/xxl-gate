package ru.inversion.msmev.mi.response;

/**
 * <h5>Тип входящего async-сообщения ответа из mi-edo.responses.</h5>
 * <p>
 * Зона ответственности:
 * - отделить ответ по отдельному item от полного request reject.
 */
public enum MiAsyncResponseKind {

   /**
    * Ответ по одной позиции payload.
    *
    * Обычно должен привести к вызову XXI item-response handler,
    * который запишет ires_code / tres_time / cres_info в XXI.
    */
   ITEM_RESPONSE,

   /**
    * Отказ по контейнеру целиком.
    *
    * Например:
    * - DTCM не прошёл XSD;
    * - MI/S забраковал весь запрос;
    * - внешний маршрут не может быть продолжен.
    *
    *  Обычно приводит к to_Error(req_id).
    */
   CONTAINER_REJECTED
}