package ru.inversion.edo.xxl.xxi.command.mi_0600;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.inversion.edo.xxl.xxi.protocol.XXLRequest;

/**
 * Подготовка команды и делегирование в Multi-Bus.
 *
 * Публичный метод пока не включён в Scheduler: до реализации
 * транспорта нет подтверждённой доставки и политики retry/claim.
 */
@Service
@RequiredArgsConstructor
public class Mi0600SendService
{
   private final Mi0600SendCommandFactory factory;
   private final Mi0600MbusPublisher publisher;

   public void send(MI_0600_Repository.PendingSendCandidate candidate)
   {
      XXLRequest command = factory.create(candidate);
      publisher.publish(command);
      // Статус MI_REQ меняет штатный обработчик после доставки через Multi-Bus.
   }
}
