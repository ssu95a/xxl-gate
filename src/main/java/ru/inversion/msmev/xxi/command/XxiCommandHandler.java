package ru.inversion.msmev.xxi.command;

import lombok.RequiredArgsConstructor;
import ru.inversion.msmev.dto.XXLResponse;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.transport.MiPublishReceipt;
import ru.inversion.msmev.transport.MiPublisher;
import ru.inversion.msmev.transport.XxlMiEnvelope;
import ru.inversion.msmev.util.Attrs;
import ru.inversion.msmev.xxi.repo.ReqRepository;
import ru.inversion.utils.U;

import java.util.Objects;

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
 * </ul>
 * <p>
 * Не принимает входящие ответы из MI.
 */
@RequiredArgsConstructor
public abstract class XxiCommandHandler {

   final private ReqRepository reqRepository;
   final private MiPublisher miPublisher;

   /** */
   abstract public int wspId();

   /** */
   abstract protected XxlMiEnvelope prepareEnvelope( XxiCommandContext context );

   /** */
   public final XXLResponse send(XxiCommandContext context)
   {
      boolean taken = false;
      boolean published = false;
      MiPublishReceipt receipt = null;

      try {
         reqRepository.take4Proc(
                 context.reqId(),
                 getClass().getSimpleName(),
                 context.callUuid()
         );

         taken = true;

         XxlMiEnvelope envelope = prepareEnvelope(context);

         receipt = Objects.requireNonNull( miPublisher.publishAsync(envelope), "MI publisher returned null receipt" );

         published = true;

         try {
            reqRepository.toSent( context.reqId(), context.callUuid() );
         } catch( Exception e ) {
            throw Errors.miPublishedStatusUpdateFailed (
                 "Контейнер был опубликован в MI, но статус запроса не был изменен на SENT (отправленный)",
                 e,
                 Attrs.merge( context.parameters(), receipt.toMap(), U.toMap( "handler", getClass().getSimpleName(), "published", true, "to_Sent", false ) )
            );
         }

         return XXLResponse.success()
                 .action(context.action())
                 .resultCode("SEND_PUBLISHED")
                 .resultInfo("Контейнер с payload отправлен в MI")
                 .parameters( context.parameters() )
                 .build();

      } catch (Exception exception) {
         throw handleSendException( context, exception, taken, published );
      }
   }

   /**
    * Базовый обработчик Exception - вызывать в блоке catch only
    * @param taken признак, что запрос был взят в обработку, т.е. был вызван take4Proc и удачно завершился
    * */
   protected RuntimeException handleSendException( XxiCommandContext context, Throwable th, boolean taken, boolean published )
   {
      if( taken && !published )
      {
         try {
            reqRepository.toError( context.reqId(), context.callUuid() );
         } catch (Throwable toErrorFailure) {
            th.addSuppressed(toErrorFailure);
         }
      }

      if( th instanceof RuntimeException )
          return (RuntimeException)th;

      return new RuntimeException(th);
   }
}
