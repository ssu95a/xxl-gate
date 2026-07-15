package ru.inversion.msmev.xxi.command;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.cbsbus.amqp.spring.handler.RequestHandler;
import ru.inversion.msmev.dto.*;
import ru.inversion.msmev.util.XxlLog;

/**
 * <h5>Entry point для команд от XXI -> XXL.</h5>
 * <p>
 * Зона ответственности:
 * <ul>
 * <li>Принимает уже десериализованный XXLRequest из Multi-Bus очереди</li>
 * <li>Не содержит бизнес-логики;/li>
 * <li>Не вызывает БД напрямую;/li>
 * <li>Делегирует обработку в XxiCommandDispatcher;/li>
 * <li>Всегда возвращает XXLResponse.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class XxiRequestHandler implements RequestHandler<XXLRequest, XXLResponse> {
    private final XxiCommandDispatcher dispatcher;
    @Nonnull
    @Override
    public XXLResponse handleRequest( @Nonnull XXLRequest request)
    {
        try( XxlLog.Scope ignored = XxlLog.module( XxlLog.Module.XXI ) )
        {
            return dispatcher.dispatch(request);
        }
    }
    @Nonnull
    @Override
    public Class<XXLRequest> getRequestType() {
        return XXLRequest.class;
    }
}