package ru.inversion.msmev.xxi.command;

import ru.inversion.msmev.dto.XXLRequest;
import ru.inversion.msmev.dto.XXLResponse;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.xxi.repo.PInf;
import ru.inversion.msmev.xxi.repo.PReq;

import java.util.Map;
import java.util.UUID;

import static ru.inversion.msmev.xxi.command.XxiCommandStatus.*;

/**
 * <h5>Runtime context команды XXI -> MI.</h5>
 * <p>
 * Request из ЦАБС назван командой, чтоб не путаться в "реквестах"
 * <p>
 * Зона ответственности:
 * <ul>
 * <li> Хранит входной XXLRequest;
 * <li> Хранит объект запроса XXI PReq из БД;
 * <li> Хранит настройки вида сведений PInf из БД;
 * <li> Предоставляет удобные методы доступа к атрибутам Dto и объектов pojo;
 * <li> Умеет проверить и сформировать response для статусов 2/3/1. тогда отправка не происходит;
 * <li> Умеет проверить, что send в MI разрешён.
 * </ul>
 * <p>
 * Не ходит в БД.
 * Не изменяет статус.
 */
public record XxiCommandContext (
     XXLRequest command,
     PReq req,
     PInf inf
)
{
   public static final String RESULT_ALREADY_IN_PROGRESS = "ALREADY_IN_PROGRESS";
   public static final String RESULT_ALREADY_SENT        = "ALREADY_SENT";
   public static final String RESULT_ALREADY_COMPLETED   = "ALREADY_COMPLETED";

   public String action() {
      return command.getAction();
   }

   public long reqId() {
      return req.getRequestId();
   }

   public int infId() { return req.getInfId(); }

   public int wspId() { return inf.getWspId(); }

   public XxiCommandStatus status() {
      return XxiCommandStatus.of( req.getStatus() );
   }

   public UUID callUuid() {
      return command.getCallUuid();
   }

   public String mode() {
      return command.getMode() == null ? null : command.getMode().trim().toLowerCase();
   }

   public boolean syncMode() {
      return "sync".equals(mode());
   }

   public boolean asyncMode() {
      return "async".equals(mode());
   }

   public boolean autoMode() {
      return "auto".equals(mode());
   }

   /** */
   public XXLResponse makeResponseOrNull( )
   {
      return switch( status() )
      {
         case STATUS_IN_WORK -> success( RESULT_ALREADY_IN_PROGRESS, "Request is already in progress" );
         case STATUS_SENT    -> success( RESULT_ALREADY_SENT,        "Request is already sent, waiting item responses");
         case STATUS_DONE    -> success( RESULT_ALREADY_COMPLETED,   "Request is already completed" );
         default             -> null;
      };
   }

   /** */
   public void checkSendAllowed()
   {
      XxiCommandStatus status = status();

      if( status == STATUS_NEW || status == STATUS_ERROR )
          return;

      throw Errors.sendNotAllowed (
         "Send is not allowed for current request status: " + status,
         parameters( )
      );
   }

   public Map<String, Object> parameters() {

      Map<String, Object> parameters = req.dump();
      parameters.put( "call_uuid", command.getCallUuid() );
      parameters.put( "mode",      command.getMode() );
      parameters.put( "action",    command.getAction() );

      return parameters;
   }

   /** */
   private XXLResponse success( String resultCode, String resultInfo )
   {
      return
         XXLResponse
           .success()
           .action( command.getAction() )
           .resultCode( resultCode )
           .resultInfo( resultInfo )
           .parameters( parameters() )
      .build();
   }
}