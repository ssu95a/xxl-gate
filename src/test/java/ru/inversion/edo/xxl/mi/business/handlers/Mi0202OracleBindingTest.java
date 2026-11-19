package ru.inversion.edo.xxl.mi.business.handlers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.db.session.SessionEnvironment;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.tc.TaskContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

class Mi0202OracleBindingTest
{
   @Test
   @EnabledIfEnvironmentVariable(named = "XXL_0202_SMOKE_JDBC_URL", matches = "jdbc:oracle:thin:.*")
   void bindsOracleWrapperTypesWithoutBusinessCall(@TempDir Path directory) throws Exception
   {
      DriverManagerDataSource dataSource = new DriverManagerDataSource(
            System.getenv("XXL_0202_SMOKE_JDBC_URL"),
            System.getenv("XXL_0202_SMOKE_DB_USER"),
            System.getenv("XXL_0202_SMOKE_DB_PASSWORD"));
      SessionEnvironment.initialize(dataSource);

      Repository_202 repository = new Repository_202(mock(XxiRepositoryExecutor.class));
      String definition;
      try (var stream = repository.defXml().openStream()) {
         definition = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }

      // Подменяем процедуру, сохраняя аргументы и преобразования рабочего вызова.
      String echoProcedure = """
            DECLARE
               PROCEDURE echo_request(
                  p_message     IN  VARCHAR2,
                  p_original    IN  VARCHAR2,
                  p_correlation IN  VARCHAR2,
                  p_time        IN  TIMESTAMP WITH TIME ZONE,
                  p_payload     IN  CLOB,
                  p_length      OUT NUMBER,
                  p_echo        OUT VARCHAR2
               ) IS
               BEGIN
                  p_length := DBMS_LOB.GETLENGTH(p_payload);
                  p_echo := p_message || '|' || p_original || '|' || p_correlation
                     || '|' || TO_CHAR(p_time, 'YYYY-MM-DD"T"HH24:MI:SS.FF6TZH:TZM')
                     || '|' || DBMS_LOB.SUBSTR(p_payload, 6000, 1);
               END;
            BEGIN
               echo_request(
            """;
      String probeDefinition = definition.replaceFirst(
            "BEGIN\\s+MI_0202_Api\\.apply_Request\\s*\\(",
            Matcher.quoteReplacement(echoProcedure));
      assertNotEquals(definition, probeDefinition);
      Path probe = Files.writeString(directory.resolve("mi-0202-binding.xml"), probeDefinition);

      UUID message = UUID.fromString("01950000-0000-7000-8000-000000000001");
      UUID original = UUID.fromString("01950000-0000-7000-8000-000000000002");
      UUID correlation = UUID.fromString("01950000-0000-7000-8000-000000000003");
      OffsetDateTime requestTime = OffsetDateTime.parse("2026-11-20T10:15:30.123456-03:30");
      String payload = "{\"probe\":\"" + "\u0416".repeat(5000) + "\"}";
      Map<String, Object> input = Map.of(
            "message_uuid", message,
            "original_request_uuid", original,
            "correlation_id", correlation,
            "request_time", requestTime,
            "payload_text", payload);

      try (TaskContext tc = new TaskContext();
           IDataCall call = SQLCallBuilder.NEW(tc)
                 .url(probe.toUri().toURL())
                 .name(repository.callName())
                 .callBackParameters(new ParametersByName()
                 {
                    @Override
                    public Object getParameter(String name)
                    {
                       return repository.bindInput(input.get(name));
                    }
                 })
                 .build()) {
         call.execute();

         assertEquals(Integer.valueOf(payload.length()), (Integer) call.getReturnValue());
         assertEquals(
               message + "|" + original + "|" + correlation + "|" + requestTime + "|" + payload,
               (String) call.get("ret_info"));
      }
   }
}
