package ru.inversion.edo.xxl.xxi.command;

import jakarta.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.inversion.cbsbus.amqp.spring.handler.RequestHandler;
import ru.inversion.edo.xxl.xxi.protocol.XXLRequest;
import ru.inversion.edo.xxl.xxi.protocol.XXLResponse;
import ru.inversion.edo.xxl.util.log.XxlLog;

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
public class XxiRequestHandler implements RequestHandler<XXLRequest, XXLResponse> {
    private final XxiCommandDispatcher dispatcher;
    private final String responseVersion;

    public XxiRequestHandler(XxiCommandDispatcher dispatcher,
                             @Value("${xxl.mbus.response-version:${XXL_MBUS_RESPONSE_VERSION:1.0}}")
                             String responseVersion) {
        if (!XXLResponse.VERSION.equals(responseVersion) && !XXLResponse.NAME_VALUE_VERSION.equals(responseVersion)) {
            throw new IllegalArgumentException("Unsupported xxl.mbus.response-version: " + responseVersion);
        }
        this.dispatcher = dispatcher;
        this.responseVersion = responseVersion;
    }

    @Nonnull
    @Override
    public XXLResponse handleRequest( @Nonnull XXLRequest request)
    {
        try( XxlLog.Scope ignored = XxlLog.module( XxlLog.Module.XXI ) )
        {
            return dispatcher.dispatch(request).withVersion(responseVersion);
        }
    }
    @Nonnull
    @Override
    public Class<XXLRequest> getRequestType() {
        return XXLRequest.class;
    }
}
