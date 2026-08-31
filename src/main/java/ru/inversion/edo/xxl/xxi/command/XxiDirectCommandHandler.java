package ru.inversion.edo.xxl.xxi.command;

import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.xxi.protocol.XXLRequest;
import ru.inversion.edo.xxl.xxi.protocol.XXLResponse;
import ru.inversion.utils.U;

import java.util.Set;

/**
 * <h5>Базовый интерфейс всех обработчиков direct команд</h5>
 */
public interface XxiDirectCommandHandler
{
   /** Список direct команд, которые обработчик поддерживает */
   Set<XxiCommandKey> commands();

   /**
    * По умолчанию direct-команда не поддерживается.
    * Handler переопределяет метод только при наличии собственного workflow.
    */
   default XXLResponse handleDirect( XxiCommandKey command, XXLRequest request )
   {
      throw Errors.contract(
              "Direct XXI command is not supported by handler",
              U.toMap(
                "handler", getClass().getName(),
                "inf_id",    command == null ? null : command.infId(),
                "action",    command == null ? null : command.action(),
                "call_uuid", request == null ? null : request.getCallUuid()
              )
      );
   }
}