package ru.inversion.msmev.mi.response;

import ru.inversion.msmev.mi.IMIEnvelope;

/**
 * <h5>Handler async-ответа MI -> XXL -> XXI.</h5>
 * <p>
 * Зона ответственности:
 * <ul>
 * <li>Применить конкретный тип async-ответа к XXI;
 * <li>Для ITEM_RESPONSE вызвать XXI item-response handler;
 * <li>Для CONTAINER_REJECTED вызвать to_Error или XXI container-reject handler;
 * <li>Вернуть ProcessResult.
 *</ul>
 * <p>
 * Не занимается:
 * - чтением очереди;
 * - парсингом transport envelope;
 * - ACK
 */
public interface MiAsyncResponseHandler {

   boolean supports( IMIEnvelope envelope );

   ProcessResult handle( IMIEnvelope envelope );
}