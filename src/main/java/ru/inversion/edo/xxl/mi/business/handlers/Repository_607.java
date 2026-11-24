package ru.inversion.edo.xxl.mi.business.handlers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.util.Attrs;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;


/**
 * Репозиторий видов сведений 607 и 608 / DOCUMENT_RECEIVED.
 * <p>
 * Вся DB-операция выполняется одним вызовом
 * <p>
 * PL/SQL {@code MI_0607_Api.process_Document_Received: }
 *<ol>
 *    <li>MI_REQ
 *    <li>MI_0600
 *    <li>MI_0600_F
 *    <li>RSD_BASES
 *    <li>RD_PKG.Parsing
 *</ol>
 * Package не выполняет COMMIT.
 * <p>
 * COMMIT сохраняет новый журнал MI_REQ/MI_0600 и результат обработки.
 * При дубле requestId, технической ошибке или нарушении JDBC-контракта
 * текущая транзакция откатывается.
 */
@Repository
@RequiredArgsConstructor
public class Repository_607
{
   private static final URL DEF_XML = Repository_607.class.getResource(
         "/ru/inversion/edo/xxl/xxi/command/mi_0600/plsql/def.xml");

   private static final String PROCESS_DOCUMENT_RECEIVED = "MI_0607.process_Document_Received";

   static final String DUPLICATE_REQUEST = "MI_0607_DUPLICATE_REQUEST";

   private static final int RET_FAIL               = -1;
   private static final int RET_OK                 = 0;
   private static final int RET_CREATE_BASE_FAILED = 1;
   private static final int RET_DOC_TYPE_MISMATCH  = 2;
   private static final int RET_PARSING_FAILED     = 3;

   private final XxiRepositoryExecutor db;


   /**
    * Обработать DOCUMENT_RECEIVED.
    * <p>
    * fileBody содержит уже декодированное Base64-содержимое поля file.
    */
   public ReceiveResult processDocumentReceived (
      int infId,
      UUID originalRequestUuid,
      UUID messageUuid,
      UUID correlationId,

      UUID taskId,
      String docType,
      OffsetDateTime docDate,

      String fileName,
      byte[] fileBody
   )
   {
      validate( originalRequestUuid, messageUuid, taskId, docType, docDate, fileName, fileBody );

      return db.execute(

              PROCESS_DOCUMENT_RECEIVED,

              U.toMap(
                      "inf_id", infId,

                      "original_request_uuid", originalRequestUuid,
                      "message_uuid", messageUuid,
                      "correlation_id", correlationId,

                      "task_id",  taskId,
                      "doc_type", docType,
                      "doc_date", docDate,

                      "file_name", fileName,
                      "file_size", fileBody.length
              ),

              tc -> callProcessDocumentReceived(
                      tc,
                      infId,

                      originalRequestUuid,
                      messageUuid,
                      correlationId,

                      taskId,
                      docType,
                      docDate,

                      fileName,
                      fileBody
              )
      );
   }


   /** */
   private static ReceiveResult callProcessDocumentReceived(

      TaskContext tc,
      int infId,

      UUID originalRequestUuid,
      UUID messageUuid,
      UUID correlationId,

      UUID taskId,
      String docType,
      OffsetDateTime docDate,

      String fileName,
      byte[] fileBody
   )
      throws Exception
   {
      try
      {
         /*
          * MI_0607_Api не выполняет COMMIT.
          *
          * Для business failure пакет откатывает только RSD-часть
          * и оставляет MI journal в текущей транзакции.
          */
         if( tc.getConnection().getAutoCommit() )
             tc.getConnection().setAutoCommit(false);

         // Для Oracle DATE передаём время в UTC через JDBC Timestamp.
         Object documentDate = tc.isPostgreSql() ? docDate
               : Timestamp.valueOf(docDate.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());

         ReceiveResult result;
         try(InputStream body = new ByteArrayInputStream(fileBody))
         {
            Attrs attrs = Attrs.create()
                    .put("inf_id", infId)
                    .put("original_request_uuid", originalRequestUuid.toString() )
                    .put("message_uuid", messageUuid.toString() )
                    .put("correlation_id", correlationId == null ? null : correlationId.toString())
                    .put("task_id",  taskId.toString() )
                    .put("doc_type", docType )
                    .put("doc_date", documentDate )
                    .put("file_name",fileName)
                    .putSensitive( "file_body", body );

            ParametersByName args = attrs.toParameters();

            try( IDataCall call = SQLCallBuilder.NEW(tc).url(DEF_XML).name(PROCESS_DOCUMENT_RECEIVED)
                     .callBackParameters(args)
                 .build()
                     .execute()
            )
            {
               result = readResult( call );
            }
         }

         if( result.duplicate() )
             tc.rollback();
         else
             tc.commit();

         return result;
      }
      catch(Exception failure)
      {
         /*
          * Unexpected DB/API/JDBC failure не является business result.
          *
          * В этом случае уничтожаем всю незавершённую операцию,
          * включая MI journal, созданный текущим вызовом.
          */
         rollbackWithSuppressed( tc, failure );

         throw failure;
      }
   }


   private static ReceiveResult readResult(IDataCall call)
   {
      Integer retCode = call.getReturnValue();
      if( retCode == null )
          throw Errors.xxiCallFailed( PROCESS_DOCUMENT_RECEIVED, 0L, RET_FAIL,
                  "out parameter '_return' is null", null );

      String retInfo = call.get("ret_info");
      if( isDuplicateRequest(retCode, retInfo) )
          return new ReceiveResult(retCode, retInfo, null, null, null, null, null);

      if(!isKnownResultCode(retCode))
      {
         throw Errors.xxiCallFailed( PROCESS_DOCUMENT_RECEIVED, 0L, retCode,
                 "MI_0607_Api returned unsupported ret_code", null );
      }


      Long reqId = call.get("req_id");
      Long itmId = call.get("itm_id");
      Long baseId = call.get("base_id");
      String fileType = call.get("file_type");
      Long parseResult = call.get("parse_result");

      // Новый журнал имеет идентификаторы и при отрицательном результате.
      if(reqId == null || itmId == null )
      {
         throw Errors.xxiCallFailed( PROCESS_DOCUMENT_RECEIVED, 0L, retCode,
                 "out parameter 'req_id' or 'itm_id' is null", null
         );
      }

      // Выходные данные RSD обязательны при успешном разборе.
      if( retCode == RET_OK )
      {
         if( baseId == null )
         {
            throw Errors.xxiCallFailed( PROCESS_DOCUMENT_RECEIVED, reqId, retCode,
                    "out parameter 'base_id' is null", null );
         }

         if( S.isNullOrEmpty(fileType) )
         {
            throw Errors.xxiCallFailed( PROCESS_DOCUMENT_RECEIVED, reqId,
                    retCode, "out parameter 'file_type' is null", null );
         }


         if( parseResult == null )
         {
            throw Errors.xxiCallFailed(
                    PROCESS_DOCUMENT_RECEIVED,
                    reqId,
                    retCode,
                    "out parameter 'parse_result' is null",
                    null
            );
         }
      }

      return new ReceiveResult(retCode, retInfo, reqId, itmId, baseId, fileType, parseResult);
   }

   private static boolean isDuplicateRequest(int retCode, String retInfo)
   {
      return retCode == RET_FAIL && retInfo != null && retInfo.startsWith(DUPLICATE_REQUEST + ":");
   }

   /** */
   private static boolean isKnownResultCode(int retCode)
   {
      return switch(retCode)
      {
         case RET_OK,
              RET_CREATE_BASE_FAILED,
              RET_DOC_TYPE_MISMATCH,
              RET_PARSING_FAILED -> true;

         default -> false;
      };
   }


   /** */
   private static void validate(
      UUID originalRequestUuid,
      UUID messageUuid,

      UUID taskId,
      String docType,
      OffsetDateTime docDate,

      String fileName,
      byte[] fileBody)
   {
      if( originalRequestUuid == null )
          throw Errors.contract( "DOCUMENT_RECEIVED.originalRequestId отсутствует" );

      if( messageUuid == null )
          throw Errors.contract( "DOCUMENT_RECEIVED messageUuid отсутствует" );

      if( taskId == null )
          throw Errors.contract( "DOCUMENT_RECEIVED.taskId отсутствует" );

      if( S.isNullOrEmpty(docType) )
          throw Errors.contract( "DOCUMENT_RECEIVED.docType отсутствует" );

      String normalizedDocType = docType.trim().toUpperCase();

      if( !"UNO".equals(normalizedDocType) && !"UPO".equals(normalizedDocType))
           throw Errors.contract( "Недопустимый DOCUMENT_RECEIVED.docType", U.toMap("doc_type", docType ) );

      if( docDate == null)
          throw Errors.contract( "DOCUMENT_RECEIVED.docDate отсутствует" );

      if(S.isNullOrEmpty(fileName))
         throw Errors.contract( "DOCUMENT_RECEIVED.fileName отсутствует" );

      if( fileName.length() > 100 )
      {
         throw Errors.contract(
            "DOCUMENT_RECEIVED.fileName превышает VARCHAR2(100)",
            U.toMap( "file_name", fileName, "length", fileName.length() )
         );
      }

      if( fileBody == null || fileBody.length == 0 )
          throw Errors.contract( "DOCUMENT_RECEIVED.file пуст" );
   }


   /** */
   private static void rollbackWithSuppressed(
           TaskContext tc,
           Exception failure)
   {
      try {
         tc.rollback();
      }
      catch(Exception rollbackFailure)
      {
         failure.addSuppressed(rollbackFailure);
      }
   }


   /**
    * Результат MI_0607_Api.process_Document_Received.
    */
   public record ReceiveResult(
           int retCode,
           String retInfo,

           Long reqId,
           Long itmId,

           Long baseId,
           String fileType,
           Long parseResult
   )
   {
      /** Документ успешно обработан. */
      public boolean success()
      {
         return retCode == RET_OK;
      }

      /** Новый DOCUMENT_RECEIVED полностью обработан. */
      public boolean processed()
      {
         return retCode == RET_OK;
      }

      /** Повтор бизнесового requestId входящего документа. */
      public boolean duplicate()
      {
         return isDuplicateRequest(retCode, retInfo);
      }

      /** Отрицательный результат обработки с сохранённым журналом. */
      public boolean businessFailed()
      {
         return retCode == RET_CREATE_BASE_FAILED || retCode == RET_DOC_TYPE_MISMATCH || retCode == RET_PARSING_FAILED;
      }
   }
}
