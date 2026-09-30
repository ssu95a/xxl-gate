package ru.inversion.edo.xxl.xxi.command.mi_0600;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.transport.PayloadDto;
import ru.inversion.edo.xxl.util.Attrs;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.edo.xxl.xxi.repo.InfRole;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.U;
import ru.inversion.utils.converter.TypeConverter;

import java.io.IOException;
import java.io.InputStream;

import java.net.URL;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.time.Duration;

import java.util.Collections;
import java.util.List;
import java.util.UUID;


@Repository
@RequiredArgsConstructor
public class MI_0600_Repository
{
   public record CreateResult(
           long reqId,
           long itmId
   )
   {
   }


   private static final URL DEF_XML =
           MI_0600_Repository.class.getResource("plsql/def.xml");

   private static final String CREATE_CALL_NAME =
           "MI_0600.create_Request";

   private static final String PREPARE_PAYLOAD_OPERATION =
           "MI_0600.preparePayload";

   private static final String SEND_PENDING_OPERATION =
           "MI_0600.send_Pending";

   private static final String GET_RECEIVE_DIR_OPERATION =
           "MI_0600.getReceiveDir";

   private static final String ZIP_MEDIA_TYPE =
           "application/zip";

   private static final String PAYLOAD_SQL =
           "select bzip_data from xxi.mi_0600 where req_id = ?";

   private static final String RECEIVE_DIR_SQL =
           """
           select MI_prp.get_Inf_Property(
              ?,
              'RECEIVE_DIR',
              null
           )
           """;


   private final XxiRepositoryExecutor db;


   /**
    * Создает исходящий request.
    *
    * Используется scheduler-ом для файлов,
    * собранных из SEND_DIR.
    */
   public CreateResult createRequest(
           int infId,
           Path zipPath,
           List<String> fileNames
   )
   {
      return createRequest(
              infId,
              zipPath,
              fileNames,

              null,       // originalRequestUuid
              null,       // messageUuid
              null,       // ctaxreqId

              0           // createType
      );
   }


   /**
    * Создает MI_0600 request + item и сохраняет ZIP в XXI.
    *
    * Используется как для исходящего, так и для входящего
    * файлового обмена.
    *
    * Для входящего MI -> XXL передаются:
    *
    * originalRequestUuid
    * messageUuid
    *
    * ZIP после успешного COMMIT становится durable source
    * в PostgreSQL.
    */
   public CreateResult createRequest(
           int infId,
           Path zipPath,
           List<String> fileNames,
           UUID originalRequestUuid,
           UUID messageUuid,
           String ctaxreqId,
           int createType
   )
   {
      if( zipPath == null || !Files.isRegularFile(zipPath) )
      {
         throw Errors.payloadBuildFailed(
                 "ZIP file does not exist",
                 null,
                 U.toMap(
                         "inf_id", infId,
                         "zip_file",
                         zipPath == null
                                 ? null
                                 : zipPath.toString()
                 )
         );
      }

      if( fileNames == null || fileNames.isEmpty() )
      {
         throw Errors.payloadBuildFailed(
                 "MI_0600 ZIP file list is empty",
                 null,
                 U.toMap(
                         "inf_id", infId,
                         "zip_file", zipPath.toString()
                 )
         );
      }


      final long zipSize;

      try
      {
         zipSize = Files.size(zipPath);
      }
      catch( IOException e )
      {
         throw Errors.payloadBuildFailed(
                 "Не удалось определить размер ZIP",
                 e,
                 U.toMap(
                         "inf_id", infId,
                         "zip_file", zipPath.toString()
                 )
         );
      }


      try(
              InputStream zipData =
                      Files.newInputStream(zipPath)
      )
      {
         Attrs arguments =
                 Attrs.create()
                         .put(
                                 "inf_id",
                                 infId
                         )
                         .put(
                                 "zip_name",
                                 zipPath
                                         .getFileName()
                                         .toString()
                         )
                         .putSensitive(
                                 "zip_data",
                                 zipData
                         )
                         .put(
                                 "zip_size",
                                 zipSize
                         )
                         .put(
                                 "zip_files_count",
                                 fileNames.size()
                         )
                         .put(
                                 "file_names",
                                 fileNames
                         )
                         /*
                          * Ключи кладем даже при null:
                          * они присутствуют в callback-контракте def.xml.
                          */
                         .put(
                                 "original_request_uuid",
                                 originalRequestUuid
                         )
                         .put(
                                 "message_uuid",
                                 messageUuid
                         )
                         .put(
                                 "ctaxreq_id",
                                 ctaxreqId
                         )
                         .put(
                                 "create_type",
                                 createType
                         );


         return db.execute(
                 CREATE_CALL_NAME,

                 /*
                  * zip_data помечен sensitive,
                  * поэтому InputStream не попадет
                  * в диагностический context.
                  */
                 arguments.toSafeMap(),

                 tc ->
                         callCreateRequest(
                                 tc,
                                 arguments.toParameters()
                         )
         );
      }
      catch( IOException e )
      {
         throw Errors.payloadBuildFailed(
                 "Не удалось открыть ZIP для сохранения в XXI",
                 e,
                 U.toMap(
                         "inf_id", infId,
                         "zip_file", zipPath.toString(),
                         "zip_size", zipSize
                 )
         );
      }
   }


   /**
    * Вызов mi_0600_api.create_request.
    */
   private CreateResult callCreateRequest(
           TaskContext tc,
           ParametersByName arguments
   )
           throws Exception
   {
      try
      {
         try(
                 IDataCall call =
                         SQLCallBuilder
                                 .NEW(tc)
                                 .url(DEF_XML)
                                 .name(CREATE_CALL_NAME)
                                 .callBackParameters(arguments)
                                 .build()
                                 .execute()
         )
         {
            Integer retCode =
                    call.getReturnValue();

            String retInfo =
                    call.get("ret_info");

            Long itmId =
                    call.get("itm_id");

            Long reqId =
                    call.get("req_id");


            if( retCode == null || retCode != 0 )
            {
               throw Errors.xxiCallFailed(
                       CREATE_CALL_NAME,
                       0L,
                       U.nvl(retCode, -1),
                       retInfo,
                       null
               );
            }

            if( reqId == null )
            {
               throw Errors.xxiCallFailed(
                       CREATE_CALL_NAME,
                       0L,
                       retCode,
                       "out parameter 'req_id' is null",
                       null
               );
            }

            if( itmId == null )
            {
               throw Errors.xxiCallFailed(
                       CREATE_CALL_NAME,
                       0L,
                       retCode,
                       "out parameter 'itm_id' is null",
                       null
               );
            }


            tc.commit();


            return new CreateResult(
                    reqId,
                    itmId
            );
         }
      }
      catch( Exception e )
      {
         tc.rollback();

         throw e;
      }
   }


   /**
    * RECEIVE_DIR для входящего вида сведений.
    */
   public Path getReceiveDir(
           int infId
   )
   {
      return db.execute(
              GET_RECEIVE_DIR_OPERATION,
              U.toMap(
                      "inf_id", infId,
                      "role", InfRole.Respondent.name()
              ),
              tc ->
              {
                 try(
                         PreparedStatement ps =
                                 tc.getConnection()
                                         .prepareStatement(RECEIVE_DIR_SQL)
                 )
                 {
                    ps.setInt(
                            1,
                            infId
                    );


                    try(
                            ResultSet rs =
                                    ps.executeQuery()
                    )
                    {
                       if( !rs.next() )
                       {
                          throw Errors.config(
                                  "Не удалось получить RECEIVE_DIR для вида сведений "
                                          + infId,
                                  U.toMap(
                                          "inf_id", infId,
                                          "role", InfRole.Respondent,
                                          "property", "RECEIVE_DIR"
                                  )
                          );
                       }


                       String value =
                               rs.getString(1);


                       if(
                               value == null ||
                                       value.isBlank()
                       )
                       {
                          throw Errors.config(
                                  "Для вида сведений "
                                          + infId
                                          + " не настроена директория приема",
                                  U.toMap(
                                          "inf_id", infId,
                                          "role", InfRole.Respondent,
                                          "property", "RECEIVE_DIR"
                                  )
                          );
                       }


                       try
                       {
                          return Path.of(
                                  value.trim()
                          );
                       }
                       catch( InvalidPathException e )
                       {
                          throw Errors.config(
                                  "Для вида сведений "
                                          + infId
                                          + " некорректно настроена директория приема",
                                  e,
                                  U.toMap(
                                          "inf_id", infId,
                                          "role", InfRole.Respondent,
                                          "property", "RECEIVE_DIR"
                                  )
                          );
                       }
                    }
                 }
              }
      );
   }


   /**
    * Материализует сохраненный в XXI ZIP
    * во временный файл для отправки в MI.
    *
    * При успешном возврате repository файл не удаляет:
    * дальнейший lifecycle принадлежит transport-слою.
    */
   public PayloadDto preparePayload(
           long reqId
   )
   {
      return db.execute(
              PREPARE_PAYLOAD_OPERATION,
              U.toMap(
                      "param_keys",
                      List.of("req_id"),

                      "param_count",
                      1
              ),
              tc ->
                      buildPayload(
                              tc,
                              reqId
                      )
      );
   }


   /**
    * Создает временный ZIP и выгружает
    * в него bytea из XXI.
    */
   private PayloadDto buildPayload(
           TaskContext tc,
           long reqId
   )
   {
      Path zipPath = null;

      boolean completed = false;


      try
      {
         zipPath =
                 createTempZipFileName(
                         reqId
                 );

         long zipSize =
                 writePayload(
                         tc,
                         reqId,
                         zipPath
                 );


         completed = true;


         return new PayloadDto(
                 ZIP_MEDIA_TYPE,
                 zipPath,
                 zipSize
         );
      }
      finally
      {
         /*
          * Если repository не смог вернуть
          * готовый payload.
          */
         if( !completed )
            deleteTempFile(zipPath);
      }
   }


   /**
    * Копирует xxi.mi_0600.bzip_data
    * во временный файл.
    */
   private long writePayload(
           TaskContext tc,
           long reqId,
           Path zipPath
   )
   {
      try(
              PreparedStatement ps =
                      tc.getConnection()
                              .prepareStatement(PAYLOAD_SQL)
      )
      {
         ps.setLong(
                 1,
                 reqId
         );


         try(
                 ResultSet rs =
                         ps.executeQuery()
         )
         {
            if( !rs.next() )
            {
               throw Errors.payloadBuildFailed(
                       "MI_0600 item не найден для request",
                       null,
                       U.toMap(
                               "req_id", reqId
                       )
               );
            }


            final long actualSize;


            try(
                    InputStream input =
                            rs.getBinaryStream(
                                    "bzip_data"
                            )
            )
            {
               if( input == null )
               {
                  throw Errors.payloadBuildFailed(
                          "MI_0600 ZIP payload отсутствует",
                          null,
                          U.toMap(
                                  "req_id", reqId
                          )
                  );
               }


               try
               {
                  actualSize =
                          Files.copy(
                                  input,
                                  zipPath,
                                  StandardCopyOption.REPLACE_EXISTING
                          );
               }
               catch( IOException e )
               {
                  throw Errors.payloadBuildFailed(
                          "Не удалось записать MI_0600 ZIP во временный файл",
                          e,
                          U.toMap(
                                  "req_id", reqId
                          )
                  );
               }
            }


            if( actualSize == 0 )
            {
               throw Errors.payloadBuildFailed(
                       "MI_0600 ZIP payload пуст",
                       null,
                       U.toMap(
                               "req_id", reqId
                       )
               );
            }


            if( rs.next() )
            {
               throw Errors.payloadBuildFailed(
                       "Для MI_0600 request найдено более одного item",
                       null,
                       U.toMap(
                               "req_id", reqId
                       )
               );
            }


            return actualSize;
         }
      }
      catch( SQLException | IOException e )
      {
         throw Errors.dbError(
                 PREPARE_PAYLOAD_OPERATION,
                 e,
                 U.toMap(
                         "req_id", reqId
                 )
         );
      }
   }


   /** */
   private Path createTempZipFileName(
           long reqId
   )
   {
      try
      {
         return Files.createTempFile(
                 "xxl_0600_" + reqId + "_",
                 ".zip"
         );
      }
      catch( IOException e )
      {
         throw Errors.payloadBuildFailed(
                 "Не удалось создать временный файл MI_0600 payload",
                 e,
                 U.toMap(
                         "req_id", reqId
                 )
         );
      }
   }


   /** */
   private void deleteTempFile(
           Path path
   )
   {
      if( path == null )
         return;


      try
      {
         Files.deleteIfExists(path);
      }
      catch( IOException ignored )
      {
      }
   }


   /**
    * Период scheduler scan.
    */
   public Duration getScanDelay()
   {
      return db.execute(
              "getScanDelay",
              Collections.emptyMap(),
              tc ->
              {
                 Duration retValue =
                         Duration.ofMinutes(5);


                 try(
                         PreparedStatement ps =
                                 tc.getConnection()
                                         .prepareStatement(
                                                 """
                                                 select MI_prp.get_Wsp_Property(
                                                    600,
                                                    'SCAN_DELAY'
                                                 )::numeric
                                                 """
                                         )
                 )
                 {
                    try(
                            ResultSet rs =
                                    ps.executeQuery()
                    )
                    {
                       if( rs.next() )
                       {
                          Object value =
                                  rs.getObject(1);

                          if( value != null )
                          {
                             retValue =
                                     Duration.ofMinutes(
                                             TypeConverter.convert(
                                                     value,
                                                     Long.class
                                             )
                                     );
                          }
                       }
                    }
                 }


                 return retValue;
              }
      );
   }


   /**
    * Конфигурация inf_id конкретной роли.
    */
   public List<InfConfig> loadInfConfigs(
           InfRole role
   )
   {
      if( role == null )
         return Collections.emptyList();


      return db.execute(
              "loadInfConfigs",
              U.toMap(
                      "role",
                      role.name()
              ),
              tc ->
                      new SQLDataSet<>(
                              tc,
                              InfConfig.class
                      )
                              .wherePredicat(
                                      "initiator_cd="
                                              + role.code()
                              )
                              .queryAllRows()
                              .execute()
                              .getRows()
      );
   }


   /**
    * Просит PG отправить накопленные request,
    * которым сейчас разрешена отправка.
    */
   public void sendPending()
   {
      db.execute(
              SEND_PENDING_OPERATION,
              Collections.emptyMap(),
              this::callSendPending
      );
   }


   /** */
   private Void callSendPending(
           TaskContext tc
   )
           throws Exception
   {
      try
      {
         try(
                 IDataCall call =
                         SQLCallBuilder
                                 .NEW(tc)
                                 .url(DEF_XML)
                                 .name(SEND_PENDING_OPERATION)
                                 .build()
                                 .execute()
         )
         {
            Integer retCode =
                    call.getReturnValue();

            String retInfo =
                    call.get("ret_info");


            if(
                    retCode == null ||
                            retCode != 0
            )
            {
               throw Errors.xxiCallFailed(
                       SEND_PENDING_OPERATION,
                       0L,
                       U.nvl(retCode, -1),
                       retInfo,
                       null
               );
            }


            tc.commit();

            return null;
         }
      }
      catch( Exception e )
      {
         tc.rollback();

         throw e;
      }
   }
}