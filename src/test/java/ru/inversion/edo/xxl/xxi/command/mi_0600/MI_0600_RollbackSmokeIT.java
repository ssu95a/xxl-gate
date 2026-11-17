package ru.inversion.edo.xxl.xxi.command.mi_0600;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MANUAL ONLY: run against a non-production Oracle DB with an exclusively
 * reserved NI_OUTFILES row (REC_STATE='0') and no MI_0600_F journal.
 *
 * This test triggers a real MI_0600_Api.create_Request, validates the journal
 * in the writer session, then injects an exception before source UPDATE.
 * All persisted-state assertions occur via a new connection after rollback.
 *
 * Required JVM properties:
 *   -Dmi0600.rollback.smoke=true
 *   -Dmi0600.rollback.sourceId=<reserved NI_OUTFILES.INT_ID>
 * Optional:
 *   -Dmi0600.rollback.infId=601
 *
 * Do not enable against production. The MI_0600 scheduler is mocked to
 * prevent a competing capture from that bean during this integration test.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "mi0600.rollback.smoke", matches = "true")
class MI_0600_RollbackSmokeIT
{
   @Autowired private MI_0600_Repository repository;
   @Autowired private DataSource dataSource;

   @MockitoBean private MI_0600_Scheduler scheduler;

   @Test
   void journalAndSourceAreRolledBackTogether() throws Exception
   {
      String idText = System.getProperty("mi0600.rollback.sourceId");
      assertNotNull(idText, "Set -Dmi0600.rollback.sourceId explicitly");
      long sourceId = Long.parseLong(idText);
      assertTrue(sourceId > 0, "Source ID must be positive");

      int infId = Integer.parseInt(System.getProperty("mi0600.rollback.infId", "601"));
      assertTrue(infId == 601 || infId == 603 || infId == 611,
            "Smoke only applies to generic outgoing 601/603/611");

      JdbcTemplate independent = new JdbcTemplate(dataSource);
      try (var connection = dataSource.getConnection())
      {
         String product = connection.getMetaData().getDatabaseProductName();
         assertTrue(product.toLowerCase().contains("oracle"),
               "This smoke is Oracle-only, database was " + product);
      }

      String filename = independent.queryForObject(
            "SELECT filename FROM xxi.ni_outfiles WHERE int_id=? AND rec_state='0'",
            String.class, sourceId);
      assertNotNull(filename, "Reserved test source must have a filename");

      Integer priorJournal = independent.queryForObject(
            "SELECT COUNT(*) FROM xxi.mi_0600_f WHERE czip_file_name=?",
            Integer.class, filename);
      assertEquals(0, priorJournal, "Do not run against an already journaled file");

      AtomicLong observedReqId = new AtomicLong(-1);
      AtomicLong observedItmId = new AtomicLong(-1);
      final String marker = "MI0600_ROLLBACK_SMOKE_AFTER_JOURNAL";

      RuntimeException failed = assertThrows(RuntimeException.class, () ->
            repository.capturePendingXml(infId, "o.int_id=" + sourceId,
                  1, 1_048_576L, 2_097_152,
                  (reqId, itmId) -> {
                     observedReqId.set(reqId);
                     observedItmId.set(itmId);
                     throw new SQLException(marker);
                  }));

      assertTrue(containsMessage(failed, marker),
            "Expected specifically injected failure, got: " + failed);
      assertTrue(observedReqId.get() > 0 && observedItmId.get() > 0,
            "Failpoint must execute after successful create_Request/journal verification");

      // Read through the independent connection *after* the repository has
      // rolled back and released the source lock.
      assertEquals("0", independent.queryForObject(
            "SELECT rec_state FROM xxi.ni_outfiles WHERE int_id=?",
            String.class, sourceId));

      assertEquals(0, independent.queryForObject(
            "SELECT COUNT(*) FROM xxi.mi_req WHERE req_id=?",
            Integer.class, observedReqId.get()));
      assertEquals(0, independent.queryForObject(
            "SELECT COUNT(*) FROM xxi.mi_0600 WHERE itm_id=?",
            Integer.class, observedItmId.get()));
      assertEquals(0, independent.queryForObject(
            "SELECT COUNT(*) FROM xxi.mi_0600_f WHERE itm_id=?",
            Integer.class, observedItmId.get()));

      assertEquals(0, independent.queryForObject(
            "SELECT COUNT(*) FROM xxi.mi_0600_f WHERE czip_file_name=?",
            Integer.class, filename));

      // Sequences/logs may be autonomous: do not assert sequence gaps
      // or absence of independently committed diagnostic log records.
   }

   private static boolean containsMessage(Throwable failure, String marker)
   {
      for (Throwable current = failure; current != null; current = current.getCause())
         if (current.getMessage() != null && current.getMessage().contains(marker))
            return true;
      return false;
   }
}
