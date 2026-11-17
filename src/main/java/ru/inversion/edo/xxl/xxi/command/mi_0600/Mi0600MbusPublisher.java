package ru.inversion.edo.xxl.xxi.command.mi_0600;

import ru.inversion.edo.xxl.xxi.protocol.XXLRequest;

/**
 * Граница отправки XXLRequest через Multi-Bus.
 *
 * Реализация должна отправлять сообщение через согласованный транспорт,
 * а не вызывать XxiCommandDispatcher напрямую. Нельзя считать публикацию
 * успешной по факту создания локального XXLRequest.
 */
public interface Mi0600MbusPublisher
{
   void publish(XXLRequest command);
}
