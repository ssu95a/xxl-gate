package ru.inversion.edo.xxl.xxi.command.mi_0600;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.xxi.command.XxiRequestValidator;
import ru.inversion.edo.xxl.xxi.protocol.XXLRequest;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Подготовка отправляемой через Multi-Bus команды.
 * Использует значения из сохранённой MI_REQ; никаких переходов статуса нет.
 */
@Component
@RequiredArgsConstructor
public class Mi0600SendCommandFactory
{
   private static final DateTimeFormatter TIMESTAMP_FORMAT =
         DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS xxx", Locale.ROOT);

   private final XxiRequestValidator validator;

   public XXLRequest create(MI_0600_Repository.PendingSendCandidate candidate)
   {
      return create(candidate, UUID.randomUUID(), OffsetDateTime.now());
   }

   // Package-private для детерминированных unit-тестов.
   XXLRequest create(MI_0600_Repository.PendingSendCandidate candidate,
                     UUID callUuid, OffsetDateTime createdAt)
   {
      Objects.requireNonNull(candidate, "candidate");
      Objects.requireNonNull(callUuid, "callUuid");
      Objects.requireNonNull(createdAt, "createdAt");

      XXLRequest command = XXLRequest.builder()
            .version("1.0")
            .action("send")
            .mode("auto")
            .requestId(candidate.reqId())
            .infId(candidate.infId())
            .externalUuid(candidate.externalUuid())
            .correlationId(candidate.correlationId())
            .callUuid(callUuid)
            .timestamp(TIMESTAMP_FORMAT.format(createdAt))
            .parametersJson("{}")
            .build();

      validator.validate(command);
      return command;
   }
}
