package ru.inversion.edo.xxl.xxi.command.mi_0600;

import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.xxi.protocol.XXLRequest;

/**
 * Заглушка транспорта до согласования интеграции с Multi-Bus.
 * TODO: заменить реализацией с подтверждёнными правилами маршрутизации,
 * подтверждения, таймаутов и повторной доставки.
 */
@Component
public final class Mi0600MbusPublisherStub implements Mi0600MbusPublisher
{
   @Override
   public void publish(XXLRequest command)
   {
      throw new UnsupportedOperationException(
            "MI_0600 Multi-Bus publication is not configured; request remains in MI_REQ");
   }
}
