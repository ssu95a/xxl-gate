package ru.inversion.msmev.xxi.command;

import ru.inversion.msmev.dto.XXLResponse;
import ru.inversion.msmev.error.XXLException;
import ru.inversion.msmev.xxi.repo.ReqRepository;

/**
 * <h6>Базовый Handler бизнес-направления XXI -> S.</h6>
 * <p>
 * Зона ответственности:
 * Выполняет конкретный workflow по виду сведений / wsp_id/inf_id
 * <ul>
 *  <li>Вызывает take_For_Proc;
 *  <li>Собирает payload;
 *  <li>Выполняет sync или async отправку;
 *  <li>Вызывает to_Sent после фактического старта внешнего маршрута;
 *  <li>Вызывает to_Error при container-level failure после take_For_Proc;
 *  <li>Возвращает SEND_PUBLISHED или SYNC_COMPLETED.
 * </u>
 * <p>
 * Не принимает входящие ответы из MI.
 */
public abstract class XxiCommandHandler {

   abstract public int wspId();

   abstract public XXLResponse send( XxiCommandContext context );

   /** */
   protected RuntimeException handleSendException( XxiCommandContext context, Throwable th, boolean taken, ReqRepository reqRepository )
   {
      // Признак, что запрос был взят в обработку
      // т.е. был вызван take4Proc
      if( taken )
      {
         try {
            reqRepository.toError( context.reqId(), context.callUuid() );
         } catch (Throwable toErrorFailure) {
            th.addSuppressed(toErrorFailure);
         }
      }

      if( th instanceof XXLException )
          return (XXLException)th;

      if( th instanceof RuntimeException )
          return (RuntimeException)th;

      return new RuntimeException(th);
   }
}
