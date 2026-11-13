package ru.inversion.edo.xxl.xxi.command.mi_0600;

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


/**
 * Repository вида сведений 607 / DOCUMENT_RECEIVED.
 *
 * Вся бизнес-операция выполняется одним вызовом XXI:
 *
 *   RD_PKG.CreateBase
 *          |
 *          v
 *   UPDATE RSD_BASES
 *          |
 *          v
 *   RD_PKG.Parsing
 *
 * COMMIT / ROLLBACK выполняются здесь.
 */
@Repository
@RequiredArgsConstructor
public class MI_0607_Repository
{
   private static final URL DEF_XML = MI_0607_Repository.class.getResource("plsql/def.xml");

   private static final String PROCESS_DOCUMENT_RECEIVED = "MI_0607.process_Document_Received";

   private final XxiRepositoryExecutor db;

   /**
    * Обработать DOCUMENT_RECEIVED.
    * <p>
    * fileBody содержит уже декодированное значение поля file из Base64.
    */
   public ReceiveResult processDocumentReceived(
           String taskId,
           String docType,
           OffsetDateTime docDate,
           String fileName,
           byte[] fileBody)
   {
      validate(taskId, docType, docDate, fileName, fileBody);

      return db.execute(
              PROCESS_DOCUMENT_RECEIVED,
              U.toMap(
                      "task_id", taskId,
                      "doc_type", docType,
                      "doc_date", docDate,
                      "file_name", fileName,
                      "file_size", fileBody.length
              ),
              tc -> processDocumentReceived(
                      tc,
                      taskId,
                      docType,
                      docDate,
                      fileName,
                      fileBody
              )
      );
   }


   /** */
   private static ReceiveResult processDocumentReceived (
      TaskContext tc,
      String taskId,
      String docType,
      OffsetDateTime docDate,
      String fileName,
      byte[] fileBody
   ) throws Exception
   {
      try
      {
         /*
          * Вызов должен быть атомарным.
          * При autoCommit=true JDBC зафиксировал бы PL/SQL-вызов раньше,
          * чем Java успела бы проанализировать ret_code.
          */
         if( tc.getConnection().getAutoCommit() )
             tc.getConnection().setAutoCommit(false);

         /*
          * Oracle DATE не содержит timezone.
          * DOCUMENT_RECEIVED.docDate по контракту задаётся в UTC,
          * поэтому передаём UTC wall-clock.
          */
         Timestamp documentDate = Timestamp.valueOf( docDate.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime() );

         try( InputStream body = new ByteArrayInputStream(fileBody) )
         {
            Attrs attrs = Attrs.create()
               .put("file_name", fileName)
               .putSensitive("file_body", body)
               .put("task_id", taskId)
               .put("doc_date", documentDate)
               .put("doc_type", docType);

            ParametersByName args = attrs.toParameters();

            try(IDataCall call = SQLCallBuilder.NEW(tc).url(DEF_XML).name(PROCESS_DOCUMENT_RECEIVED).callBackParameters(args).build().execute() )
            {
               Integer retCode = call.getReturnValue();
               String retInfo  = call.get("ret_info");
               Long baseId     = call.get("base_id");
               String fileType = call.get("file_type");
               Integer parseResult = call.get("parse_result");

               if( retCode == null )
                   throw Errors.xxiCallFailed ( PROCESS_DOCUMENT_RECEIVED, 0L, -1, "out parameter '_return' is null", null );

               ReceiveResult result = new ReceiveResult( retCode, retInfo, baseId, fileType, parseResult );


               /*
                * Ошибка CreateBase / несовпадение типа / ошибка Parsing.
                *
                * PL/SQL ничего не коммитит.
                * Откатываем CreateBase и все изменения Parsing.
                *
                * Это штатный результат бизнес-вызова, поэтому здесь
                * не превращаем его автоматически в DB exception.
                */
               if( retCode != 0 )
               {
                  tc.rollback();
                  return result;
               }


               /*
                * ret_code=0 обязан означать полностью сформированный
                * результат бизнес-операции.
                */
               if( baseId == null )
                   throw Errors.xxiCallFailed( PROCESS_DOCUMENT_RECEIVED, 0L, retCode, "out parameter 'base_id' is null", null );

               if( fileType == null || fileType.isBlank() )
                   throw Errors.xxiCallFailed( PROCESS_DOCUMENT_RECEIVED, 0L, retCode, "out parameter 'file_type' is null", null );

               if( parseResult == null )
                  throw Errors.xxiCallFailed( PROCESS_DOCUMENT_RECEIVED, 0L, retCode, "out parameter 'parse_result' is null", null );

               tc.commit();

               return result;
            }
         }
      }
      catch( Exception failure ) {
         try {
            tc.rollback();
         }
         catch(Exception rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
         }
         throw failure;
      }
   }


   /** */
   private static void validate (
      String taskId,
      String docType,
      OffsetDateTime docDate,
      String fileName,
      byte[] fileBody
   )
   {
      if(S.isNullOrEmpty(taskId) )
         throw Errors.contract( "DOCUMENT_RECEIVED.taskId отсутствует" );

      if( taskId.length() > 40 )
          throw Errors.contract( "DOCUMENT_RECEIVED.taskId превышает VARCHAR2(40)", U.toMap( "task_id", taskId, "length", taskId.length() ) );

      if( S.isNullOrEmpty(docType) )
          throw Errors.contract( "DOCUMENT_RECEIVED.docType отсутствует" );

      if(docDate == null)
         throw Errors.contract( "DOCUMENT_RECEIVED.docDate отсутствует" );

      if( S.isNullOrEmpty(fileName) )
          throw Errors.contract( "DOCUMENT_RECEIVED.fileName отсутствует" );

      if(fileName.length() > 100)
         throw Errors.contract( "DOCUMENT_RECEIVED.fileName превышает VARCHAR2(100)", U.toMap( "file_name", fileName, "length", fileName.length() ) );

      if( fileBody == null || fileBody.length == 0)
         throw Errors.contract( "DOCUMENT_RECEIVED.file пуст" );
   }


   /**
    * Результат единственного бизнес-вызова XXI.
    *
    * retCode:
    *   0 - OK
    *   1 - CREATE_BASE_FAILED
    *   2 - DOC_TYPE_MISMATCH
    *   3 - PARSING_FAILED
    */
   public record ReceiveResult(
      int retCode,
      String retInfo,
      Long baseId,
      String fileType,
      Integer parseResult)
   {
      public boolean success() { return retCode == 0; }
   }
}