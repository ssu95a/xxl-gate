package ru.inversion.edo.xxl.xxi.command.mi_0600;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.dataset.SQLDataSet;
import ru.inversion.dataset.impl.XXIDsDao;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.transport.PayloadDto;
import ru.inversion.edo.xxl.util.Attrs;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.edo.xxl.xxi.repo.InfRole;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;
import ru.inversion.utils.U;
import ru.inversion.utils.io.RawBAOS;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CharsetEncoder;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Доступ к общему MI_0600 для Oracle и PostgreSQL.
 * <p>
 * Исходящий поток: NI_OUTFILES -> ZIP в памяти -> MI_0600_Api.create_Request
 * -> rec_state='1'. Одна транзакция на ZIP, публикация только после COMMIT.
 * <p>
 * Входящий поток регистрируется через createIncomingRequest(...).
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class MI_0600_Repository
{
   public record CreateResult(long reqId, long itmId, boolean existing) { }

   private record SourceFile(long intId, String filename) { }
   private record CapturedZip(byte[] bytes, List<SourceFile> files)
   {
      List<String> fileNames()
      {
         return files.stream().map(SourceFile::filename).toList();
      }
   }
   private record CaptureReceipt(int fileCount, long reqId, long itmId, int zipBytes) { }

   private static final URL DEF_XML = MI_0600_Repository.class.getResource("plsql/def.xml");
   private static final String CREATE_CALL_NAME = "MI_0600.create_Request";
   private static final String PREPARE_PAYLOAD_OPERATION = "MI_0600.preparePayload";
   private static final String SEND_PENDING_OPERATION = "MI_0600.send_Pending";
   private static final String GET_RECEIVE_DIR_OPERATION = "MI_0600.getReceiveDir";
   private static final String ZIP_MEDIA_TYPE = "application/zip";
   private static final String RECEIVE_DIR_SQL = "select MI_prp.get_Inf_Property( ?, 'RECEIVE_DIR', null )";

   private static final int RET_OK = 0;
   private static final int RET_EXISTS = 4;

   /**
    * Ограничение объёма ZIP при чтении для транспорта.
    */
   private static final int MAX_TRANSPORT_ZIP_BYTES = 128 * 1024 * 1024;
   private static final int CONSISTENCY_WARN_LIMIT = 100;
   /* SQL for each database is declared in Mi0600CaptureSql. */
   private static final String MARK_SOURCE_SQL = """
         UPDATE xxi.ni_outfiles
            SET rec_state = '1'
          WHERE int_id = ? AND filename = ? AND rec_state = '0'
         """;

   private final XxiRepositoryExecutor db;

   private static String captureSql(TaskContext tc, String key)
   {
      String sql = XXIDsDao.getNativeQuery(
            tc, Mi0600CaptureSql.class, "Mi0600CaptureSql." + key);
      if (sql == null || sql.isBlank())
         throw new IllegalStateException("MI_0600 SQL not configured: " + key);
      return sql;
   }

   /**
    * Регистрирует один ZIP с несколькими XML в единой транзакции.
    * CAPTURE_PREDICATE задаётся в MI_PRP для конкретного inf_id.
    * Лимиты ограничивают число записей, исходный объём XML и размер ZIP.
    *
    * @return число XML, зарегистрированных и подтверждённых COMMIT
    */
   public int capturePendingXml(
         int infId,
         String predicate,
         int maxFiles,
         long maxXmlBytes,
         int maxZipBytes
   )
   {
      if (infId != 601 && infId != 603 && infId != 611)
         throw new IllegalArgumentException("MI_0600 invalid initiator infId: " + infId);
      if( predicate == null || predicate.isBlank() )
         return 0; // Нет маршрута: ничего не выбираем.
      if (maxFiles < 1 || maxFiles > 1000)
         throw new IllegalArgumentException("MI_0600 maxFiles must be 1..1000");
      if (maxXmlBytes < 1 || maxZipBytes < 1 || maxZipBytes > MAX_TRANSPORT_ZIP_BYTES)
         throw new IllegalArgumentException("MI_0600 invalid XML/ZIP size limits");

      CaptureReceipt receipt = db.execute(
            "MI_0600.capturePendingXml",
            U.toMap("inf_id", infId, "capture_predicate", predicate, "max_files", maxFiles),
            tc -> {
               try {
                  // TaskContext использует отдельное соединение из пула.
                  // Без этого JDBC выполнял бы каждый запрос с отдельным COMMIT.
                  if (tc.getConnection().getAutoCommit())
                     tc.getConnection().setAutoCommit(false);
                  warnSourceInconsistencies(tc, infId, predicate);

                  CapturedZip zip = buildSourceZip(tc, predicate,
                        maxFiles, maxXmlBytes, maxZipBytes);

                  if (zip.files().isEmpty()) {
                     // Завершаем транзакцию без изменений и освобождаем snapshot.
                     tc.rollback();
                     return new CaptureReceipt(0, 0, 0, 0);
                  }

                  String zipName = "xxl_" + infId + "_" + UUID.randomUUID() + ".zip";
                  // Одна сессия и транзакция: вложенный COMMIT запрещён.
                  CreateResult created;
                  try (InputStream zipStream = new ByteArrayInputStream(zip.bytes())) {
                     ParametersByName args = createZipParameters(infId, zipName,
                           zipStream, zip.bytes().length, zip.fileNames(), 1);
                     created = callCreateRequestNoCommit(tc, args, false);
                  }

                  markSourceRows(tc, zip.files());
                  tc.commit();
                  return new CaptureReceipt(zip.fileNames().size(), created.reqId(),
                        created.itmId(), zip.bytes().length);
               }
               catch (Exception failure) {
                  rollbackWithSuppressed(tc, failure);
                  throw failure;
               }
            }
      );

      if (receipt.fileCount() > 0)
         log.info("MI_0600: пакет XML зарегистрирован: infId={}, reqId={}, itmId={}, " +
               "files={}, zipBytes={}", infId, receipt.reqId(), receipt.itmId(),
               receipt.fileCount(), receipt.zipBytes());
      return receipt.fileCount();
   }

   /**
    * Диагностируем рассинхронизацию без исключений и без исправления данных.
    * Из-за глобальной уникальности имени проверка MI выполняется по filename.
    */
   private void warnSourceInconsistencies(TaskContext tc, int infId, String predicate)
         throws SQLException
   {
      String sql = captureSql(tc, "inconsistencies").formatted(predicate);
      try(PreparedStatement ps = tc.getConnection().prepareStatement(sql))
      {
         ps.setInt(1, CONSISTENCY_WARN_LIMIT + 1);
         try(ResultSet rs = ps.executeQuery())
         {
            int warned = 0;
            while(rs.next())
            {
               if(++warned > CONSISTENCY_WARN_LIMIT)
               {
                  log.warn("MI_0600: число несогласованных записей превышает {}: inf_id={}",
                        CONSISTENCY_WARN_LIMIT, infId);
                  break;
               }
               log.warn("MI_0600: источник REC_STATE=0 уже имеет MI journal: " +
                     "inf_id={}, int_id={}, filename={}",
                     infId, rs.getLong("int_id"), rs.getString("filename"));
            }
         }
      }
   }

   /**
    * Locks source rows and streams their CLOB content into UTF-8 ZIP entries.
    * SELECT and CLOB read use one JDBC connection; locks remain held until
    * the caller commits the entire journal + source-state transition.
    */
   private CapturedZip buildSourceZip(TaskContext tc, String predicate,
                                     int maxFiles, long maxXmlBytes, int maxZipBytes)
         throws SQLException, IOException
   {
      List<SourceFile> files = new ArrayList<>();
      Set<String> names = new HashSet<>();

      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      String sql = captureSql(tc, "selectSource").formatted(predicate);

      try (PreparedStatement ps = tc.getConnection().prepareStatement(sql))
      {
         ps.setInt(1, maxFiles);

         try (ResultSet rs = ps.executeQuery();
              OutputStream zipLimit = new BoundedOutputStream(bytes, maxZipBytes);
              ZipOutputStream zip = new ZipOutputStream(zipLimit, StandardCharsets.UTF_8);
              PreparedStatement fileStmt = tc.getConnection().prepareStatement(
                    captureSql(tc, "fileData")))
         {
            BoundedOutputStream sourceLimit = new BoundedOutputStream(zip, maxXmlBytes);

            while (rs.next())
            {
               long intId = rs.getLong("int_id");
               String filename = rs.getString("filename");
               validateZipEntryName(filename);

               if (!names.add(filename))
                  throw new IOException("Duplicate filename in MI_0600 source batch: " + filename);

               fileStmt.setLong(1, intId);

               try (ResultSet fileRs = fileStmt.executeQuery())
               {
                  if (!fileRs.next())
                     throw new SQLException("NI_OUTFILES.FILEBODY not found: int_id=" + intId);

                  try (Reader body = fileRs.getCharacterStream("file_data"))
                  {
                     if (body == null)
                        throw new IOException("NI_OUTFILES.FILEBODY is NULL: int_id=" + intId);

                     zip.putNextEntry(new ZipEntry(filename));
                     long priorBytes = sourceLimit.written();
                     copyUtf8Xml(body, sourceLimit);

                     if (sourceLimit.written() == priorBytes)
                        throw new IOException("NI_OUTFILES.FILEBODY is empty: int_id=" + intId);

                     zip.closeEntry();
                     files.add(new SourceFile(intId, filename));
                  }

                  if (fileRs.next())
                     throw new SQLException("NI_OUTFILES source returned multiple bodies: int_id=" + intId);
               }
            }
         }
      }

      return files.isEmpty()
            ? new CapturedZip(new byte[0], List.of())
            : new CapturedZip(bytes.toByteArray(), List.copyOf(files));
   }

   private static final Pattern XML_ENCODING = Pattern.compile(
         "\\bencoding\\s*=\\s*(['\"])([^'\"]+)\\1", Pattern.CASE_INSENSITIVE);

   /**
    * Strict UTF-8 encoder with no materialization of the full document.
    * Closing the writer finalizes charset validation without closing ZIP.
    */
   private static void copyUtf8Xml(Reader source, OutputStream target)
         throws IOException
   {
      PushbackReader reader = new PushbackReader(source, 1024);
      validateXmlDeclaration(reader);

      CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

      try (Writer writer = new OutputStreamWriter(new NonClosingOutputStream(target), encoder))
      {
         char[] buffer = new char[8192];
         int count;
         while ((count = reader.read(buffer)) != -1)
            writer.write(buffer, 0, count);
      }
   }

   /**
    * A declared encoding different from UTF-8 must fail, not create
    * a ZIP entry whose bytes disagree with the XML declaration.
    */
   private static void validateXmlDeclaration(PushbackReader reader) throws IOException
   {
      char[] prefix = new char[1024];
      int count = 0;
      int ch;
      while (count < prefix.length && (ch = reader.read()) != -1)
      {
         prefix[count++] = (char) ch;
         if (ch == '>') break;
      }

      String header = new String(prefix, 0, count);
      String withoutBom = header.startsWith("\uFEFF") ? header.substring(1) : header;

      if (withoutBom.startsWith("<?xml"))
      {
         if (!withoutBom.contains("?>"))
            throw new IOException("Unterminated or oversized XML declaration");

         Matcher match = XML_ENCODING.matcher(withoutBom);
         if (match.find() && !"UTF-8".equalsIgnoreCase(match.group(2).trim()))
            throw new IOException("XML declaration encoding must be UTF-8: " + match.group(2));
      }

      reader.unread(prefix, 0, count);
   }

   private static final class NonClosingOutputStream extends FilterOutputStream
   {
      NonClosingOutputStream(OutputStream out) { super(out); }

      @Override public void close() throws IOException { flush(); }
   }

   private static void validateZipEntryName(String name) throws IOException
   {
      if (name == null || name.isBlank() || name.length() > 300 ||
            name.equals(".") || name.equals("..") ||
            name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 ||
            name.indexOf('\0') >= 0)
         throw new IOException("MI_0600 unsafe XML filename: " + name);
   }

   /** Меняем статус только тех записей, которые вошли в сохранённый ZIP. */
   private void markSourceRows(TaskContext tc, List<SourceFile> files) throws SQLException
   {
      try(PreparedStatement ps = tc.getConnection().prepareStatement(MARK_SOURCE_SQL))
      {
         for(SourceFile file : files)
         {
            ps.setLong(1, file.intId());
            ps.setString(2, file.filename());
            int updated = ps.executeUpdate();
            if(updated != 1)
               throw new SQLException("MI_0600: обновлено " + updated +
                     " записей NI_OUTFILES для int_id=" + file.intId() +
                     ", filename=" + file.filename() + "; ожидалась одна");
         }
      }
   }

   private static ParametersByName createZipParameters(
         int infId, String zipName, InputStream zipData, int zipSize,
         List<String> names, int createType)
   {
      Attrs args = Attrs.create()
            .put("inf_id", infId)
            .put("zip_name", zipName)
            .putSensitive("zip_data", zipData)
            .put("zip_size", zipSize)
            .put("zip_files_count", names.size())
            .put("file_names", names)
            .put("original_request_uuid", null)
            .put("message_uuid", null)
            .put("ctaxreq_id", null)
            .put("create_type", createType);
      return args.toParameters();
   }

   /** Ограничивает число записанных байтов до выделения дополнительной памяти. */
   private static final class BoundedOutputStream extends FilterOutputStream
   {
      private final long maxBytes;
      private long written;

      long written() { return written; }

      BoundedOutputStream(OutputStream delegate, long maxBytes) {
         super(delegate);
         this.maxBytes = maxBytes;
      }

      @Override public void write(int b) throws IOException {
         if (written >= maxBytes)
            throw new IOException("MI_0600 ZIP/XML size limit exceeded (" + maxBytes + " bytes)");
         out.write(b);
         written++;
      }

      @Override public void write(byte[] b, int offset, int length) throws IOException {
         Objects.checkFromIndexSize(offset, length, b.length);
         if (length > maxBytes - written)
            throw new IOException("MI_0600 ZIP/XML size limit exceeded (" + maxBytes + " bytes)");
         out.write(b, offset, length);
         written += length;
      }
   }

   private static void rollbackWithSuppressed(TaskContext tc, Exception failure)
   {
      try {
         tc.rollback();
      }
      catch (Exception rollbackFailure) {
         failure.addSuppressed(rollbackFailure);
      }
   }

   /** Директория публикации файлов входящего MI_0600 для inf_id 602/604/612. */
   public Path getReceiveDir(int infId)
   {
      return db.execute(GET_RECEIVE_DIR_OPERATION,
            U.toMap("inf_id", infId, "role", InfRole.Respondent.name()),
            tc -> {
               try (PreparedStatement ps = tc.getConnection().prepareStatement(RECEIVE_DIR_SQL)) {

                  ps.setInt(1, infId);
                  try (ResultSet rs = ps.executeQuery())
                  {
                     if (!rs.next())
                        throw Errors.config("Не удалось получить RECEIVE_DIR для вида сведений " + infId, U.toMap("inf_id", infId, "role", InfRole.Respondent, "property", "RECEIVE_DIR") );

                     String value = rs.getString(1);
                     if( S.isNullOrEmpty(value))
                         throw Errors.config("Для вида сведений " + infId + " не настроена директория приема", U.toMap("inf_id", infId, "role", InfRole.Respondent, "property", "RECEIVE_DIR"));

                     try { return Path.of(value.trim()); }
                     catch (InvalidPathException failure) {
                        throw Errors.config("Для вида сведений " + infId + " некорректно настроена директория приема", failure, U.toMap("inf_id", infId, "role", InfRole.Respondent, "property", "RECEIVE_DIR"));                     }
                  }
               }
            }
            );
   }

   /**
    * ZIP хранится в БД и передаётся транспорту как byte[] без файлового I/O.
    */
   public PayloadDto preparePayload(long reqId)
   {
      return db.execute( PREPARE_PAYLOAD_OPERATION,
            U.toMap("param_keys", List.of("req_id"), "param_count", 1),
            tc -> {

             try( PreparedStatement ps = tc.getConnection().prepareStatement("select bzip_data from xxi.mi_0600 where req_id = ?") )
               {
                  ps.setLong(1, reqId);

                  try( ResultSet rs = ps.executeQuery() )
                  {
                     if( !rs.next() )
                         throw Errors.payloadBuildFailed("MI_0600 item не найден для request", null, U.toMap("req_id", reqId));

                     byte[] zipData;
                     try (InputStream stream = rs.getBinaryStream("bzip_data"))
                     {
                        if( stream == null)
                            throw Errors.payloadBuildFailed("MI_0600 ZIP payload отсутствует", null, U.toMap("req_id", reqId));
                        zipData = readBinaryLimited(stream, MAX_TRANSPORT_ZIP_BYTES);
                     }

                     if(zipData.length == 0)
                        throw Errors.payloadBuildFailed("MI_0600 ZIP payload пуст", null, U.toMap("req_id", reqId));

                     if(rs.next())
                        throw Errors.payloadBuildFailed("Для MI_0600 request найдено более одного item", null, U.toMap("req_id", reqId));

                     return new PayloadDto(ZIP_MEDIA_TYPE, zipData, zipData.length);
                  }
               }
               catch (IOException failure) {
                  throw Errors.payloadBuildFailed("MI_0600 ZIP payload не удалось прочитать или превышен лимит памяти", failure, U.toMap("req_id", reqId));
               }
               catch (SQLException failure) {
                  throw Errors.dbError(PREPARE_PAYLOAD_OPERATION, failure, U.toMap("req_id", reqId));
               }
            }
         );
   }


   /** */
   private static byte[] readBinaryLimited( InputStream in, int maxBytes ) throws IOException
   {
      final RawBAOS result = new RawBAOS();

      try( OutputStream bounded = new BoundedOutputStream(result, maxBytes) ) {
           in.transferTo(bounded);
      }

      return result.buf();
   }

   /** */
   public Duration getScanDelay()
   {
      return db.execute("getScanDelay", Collections.emptyMap(), tc -> {
         List<Mi0600ScanDelay> rows =
               new SQLDataSet<>(tc, Mi0600ScanDelay.class)
                     .queryAllRows()
                     .execute()
                     .getRows();

         if (rows.size() != 1)
            throw Errors.config(
                  "MI_0600 SCAN_DELAY query must return exactly one row",
                  U.toMap("rows", rows.size())
            );

         Long minutes = rows.get(0).getScanDelay();
         return minutes == null ? Duration.ofMinutes(5) : Duration.ofMinutes(minutes);
      });
   }

   /** */
   public List<InfConfig> loadInfConfigs(InfRole role)
   {
      if( role == null )
          return Collections.emptyList();
      return db.execute("loadInfConfigs", U.toMap("role", role.name()),tc -> new SQLDataSet<>(tc, InfConfig.class).wherePredicat("initiator_cd=" + role.code()).queryAllRows().execute().getRows());
   }

   /** */
   public void sendPending()
   {
      db.execute(SEND_PENDING_OPERATION, Collections.emptyMap(), this::callSendPending);
   }

   /** */
   private Void callSendPending(TaskContext tc) throws Exception
   {
      try {
         try (IDataCall call = SQLCallBuilder.NEW(tc).url(DEF_XML)
               .name(SEND_PENDING_OPERATION).build().execute()) {
            Integer retCode = call.getReturnValue();
            String retInfo = call.get("ret_info");
            if (retCode == null || retCode != 0)
               throw Errors.xxiCallFailed(SEND_PENDING_OPERATION, 0L,
                     U.nvl(retCode, -1), retInfo, null);
            tc.commit();
            return null;
         }
      }
      catch (Exception failure) {
         rollbackWithSuppressed(tc, failure);
         throw failure;
      }
   }

   /** */
   public CreateResult createIncomingRequest( int infId, Path zipPath, List<String> fileNames, UUID originalRequestUuid, UUID messageUuid )
   {
      return createRequest(infId, zipPath, fileNames, originalRequestUuid, messageUuid, null, 0, true);
   }

   /** */
   private CreateResult createRequest( int infId, Path zipPath, List<String> fileNames, UUID originalRequestUuid, UUID messageUuid, String cTaxReqId, int createType, boolean allowExists )
   {
      if( zipPath == null || !Files.isRegularFile(zipPath) )
          throw Errors.payloadBuildFailed( "ZIP file does not exist", null, U.toMap("inf_id", infId, "zip_file", zipPath == null ? null : zipPath.toString()) );

      if( fileNames == null || fileNames.isEmpty() )
          throw Errors.payloadBuildFailed("MI_0600 ZIP file list is empty", null, U.toMap("inf_id", infId, "zip_file", zipPath.toString()));

      final long zipSize;

      try {
         zipSize = Files.size(zipPath);
      }
      catch (IOException failure) {
         throw Errors.payloadBuildFailed("Не удалось определить размер ZIP", failure, U.toMap("inf_id", infId, "zip_file", zipPath.toString()));
      }

      try (InputStream zipData = Files.newInputStream(zipPath)) {
         Attrs args = Attrs.create()
               .put("inf_id", infId)
               .put("zip_name", zipPath.getFileName().toString())
               .putSensitive("zip_data", zipData)
               .put("zip_size", zipSize)
               .put("zip_files_count", fileNames.size())
               .put("file_names", fileNames)
               .put("original_request_uuid", originalRequestUuid)
               .put("message_uuid", messageUuid)
               .put("ctaxreq_id", cTaxReqId)
               .put("create_type", createType);

         return db.execute(CREATE_CALL_NAME, args.toSafeMap(), tc -> {
            try {
               CreateResult result = callCreateRequestNoCommit(tc, args.toParameters(), allowExists);
               tc.commit();
               return result;
            }
            catch (Exception failure) {
               rollbackWithSuppressed(tc, failure);
               throw failure;
            }
         });
      }
      catch (IOException failure) {
         throw Errors.payloadBuildFailed("Не удалось открыть ZIP для сохранения в XXI", failure,
               U.toMap("inf_id", infId, "zip_file", zipPath.toString(), "zip_size", zipSize));
      }
   }

   /**
    * Внутренняя регистрация без COMMIT и ROLLBACK. Транзакцией владеет вызывающий код.
    */
   private CreateResult callCreateRequestNoCommit( TaskContext tc, ParametersByName args, boolean allowExists)
   {
      try( IDataCall call = SQLCallBuilder.NEW(tc).url(DEF_XML).name(CREATE_CALL_NAME).callBackParameters(args).build().execute() ) {

         Integer retCode = call.getReturnValue();
         String retInfo = call.get("ret_info");
         Long itmId = call.get("itm_id");
         Long reqId = call.get("req_id");

         boolean accepted = retCode != null && (retCode == RET_OK || (allowExists && retCode == RET_EXISTS));

         if(!accepted)
             throw Errors.xxiCallFailed(CREATE_CALL_NAME, 0L, U.nvl(retCode, -1), retInfo, null);

         if( reqId == null )
             throw Errors.xxiCallFailed(CREATE_CALL_NAME, 0L, retCode, "out parameter 'req_id' is null", null);

         if( itmId == null )
             throw Errors.xxiCallFailed(CREATE_CALL_NAME, 0L, retCode, "out parameter 'itm_id' is null", null);

         return new CreateResult(reqId, itmId, retCode == RET_EXISTS);
      }
   }
}
