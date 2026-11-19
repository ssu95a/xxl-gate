package ru.inversion.edo.xxl.mi.business.handlers;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import ru.inversion.db.session.SessionEnvironment;
import ru.inversion.edo.xxl.mi.business.MiBusinessPayload;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequest;
import ru.inversion.edo.xxl.mi.business.MiBusinessResult;
import ru.inversion.edo.xxl.xxi.availability.XxiAvailabilityService;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.mi.transport.async.payload.ReceivedPayload;
import ru.inversion.tc.TaskContext;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "XXL_0202_SMOKE_JDBC_URL", matches = "jdbc:oracle:thin:.*")
class Mi0202OracleRepositoryTest
{
   private static Handler_202 handler;
   private static JdbcTemplate observer;

   @BeforeAll
   static void initialize() throws Exception
   {
      DriverManagerDataSource dataSource = new DriverManagerDataSource(
            System.getenv("XXL_0202_SMOKE_JDBC_URL"),
            System.getenv("XXL_0202_SMOKE_DB_USER"),
            System.getenv("XXL_0202_SMOKE_DB_PASSWORD"));
      SessionEnvironment.initialize(dataSource);

      XxiAvailabilityService availability = new XxiAvailabilityService(dataSource, 0);
      XxiRepositoryExecutor executor = new XxiRepositoryExecutor(TaskContext::new, availability);
      handler = new Handler_202(new Repository_202(executor));
      observer = new JdbcTemplate(dataSource);
   }

   @Test
   void rejectsNonObjectWithoutRequest() throws Exception
   {
      MiBusinessRequest request = request("[]");
      MiBusinessResult result = handler.handle(request);

      assertFalse(result.success());
      assertEquals("-1", result.responseCode(), result::responseInfo);
      assertNotNull(result.responseInfo());
      assertTrue(result.responseInfo().startsWith("MI_0202_INVALID_PAYLOAD:"), result::responseInfo);
      assertEquals(request.requestId(), result.originalRequestId());
      assertEquals(request.correlationId(), result.attributes().get("correlation_id"));
      assertTrue(persistedRequests(request).isEmpty(), "Отклонённый запрос записан в MI_REQ");
   }

   @Test
   @EnabledIfEnvironmentVariable(named = "XXL_0202_SMOKE_PAYLOAD_FILE", matches = ".+")
   void appliesFixtureAndPersistsRequestAndItem() throws Exception
   {
      // Выполняет ФПЗ и сохраняет тестовый запрос. Файл предназначен для dev.
      String json = Files.readString(Path.of(System.getenv("XXL_0202_SMOKE_PAYLOAD_FILE")));
      MiBusinessRequest request = request(json);
      MiBusinessResult result = handler.handle(request);

      assertTrue(result.success(), result::responseInfo);
      assertEquals(request.requestId(), result.originalRequestId());
      assertEquals(request.correlationId(), result.attributes().get("correlation_id"));
      assertNotNull(result.responseInfo());
      assertTrue(result.responseInfo().toUpperCase(Locale.ROOT).startsWith("OK"), result::responseInfo);

      List<PersistedRequest> rows = persistedRequests(request);
      assertEquals(1, rows.size(), "Ожидались один сохранённый запрос и один item");
      PersistedRequest row = rows.getFirst();
      assertNotNull(row.itemId(), "Для запроса отсутствует item MI_0202");
      System.out.printf("CORRELATION_ID=%s REQ_ID=%d ITM_ID=%s STATUS_CD=%d%n",
            request.correlationId(), row.requestId(), row.itemId(), row.statusCode());
   }

   private static MiBusinessRequest request(String json) throws Exception
   {
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
      ReceivedPayload source = mock(ReceivedPayload.class);
      when(source.contentType()).thenReturn(MediaType.APPLICATION_JSON_VALUE);
      when(source.openStream()).thenAnswer(ignored -> new ByteArrayInputStream(bytes));
      MiBusinessPayload payload = new MiBusinessPayload(source, MediaType.APPLICATION_JSON);
      UUID requestId = UUID.randomUUID();

      return new MiBusinessRequest(
            202, requestId, requestId, null, UUID.randomUUID(),
            "test", null, OffsetDateTime.now(), null, null,
            payload, Map.of(), Map.of());
   }

   private static List<PersistedRequest> persistedRequests(MiBusinessRequest request)
   {
      return observer.query("""
            select r.req_id, r.status_cd, i.itm_id
              from mi_req r left join mi_0202 i on i.req_id = r.req_id
             where r.inf_id = 202 and r.correlation_id = ? and r.message_uuid = ?
            """,
            (row, index) -> new PersistedRequest(row.getLong("req_id"), row.getInt("status_cd"),
                  row.getBigDecimal("itm_id")),
            request.correlationId().toString(), request.messageId().toString());
   }

   private record PersistedRequest(long requestId, int statusCode, Number itemId)
   {
   }
}
