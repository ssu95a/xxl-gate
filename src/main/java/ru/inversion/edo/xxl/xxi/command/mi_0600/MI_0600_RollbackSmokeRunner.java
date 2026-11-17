package ru.inversion.edo.xxl.xxi.command.mi_0600;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manual one-shot Oracle rollback probe. Activated only by both an explicit
 * Spring profile and explicit enabled property. NEVER enable in production.
 *
 * Uses a normal started Spring application and production DB wiring. The
 * MI_0600 scheduler is disabled while this profile is active, but other
 * application background jobs are not disabled.
 */
@Component
@Profile("mi0600-rollback-smoke")
@ConditionalOnProperty(name = "mi0600.rollback-smoke.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public final class MI_0600_RollbackSmokeRunner implements ApplicationRunner
{
   private static final String MARKER = "MI0600_ROLLBACK_SMOKE_AFTER_JOURNAL";

   private final MI_0600_Repository repository;
   private final DataSource dataSource;

   @Override
   public void run(ApplicationArguments args) throws Exception
   {
      String textId = requiredOption(args, "mi0600.rollback-smoke.source-id");
      long sourceId = Long.parseLong(textId);
      if (sourceId <= 0)
         throw new IllegalArgumentException("source-id must be positive");

      int infId = Integer.parseInt(option(args, "mi0600.rollback-smoke.inf-id", "601"));
      if (infId != 601 && infId != 603 && infId != 611)
         throw new IllegalArgumentException("Unexpected outgoing INF_ID: " + infId);

      AtomicLong reqId = new AtomicLong(-1);
      AtomicLong itmId = new AtomicLong(-1);
      String filename;

      // Check DB and source before any write. Close this independent read
      // connection before invoking capture, so one pool slot suffices.
      try (Connection c = dataSource.getConnection())
      {
         if (!c.getMetaData().getDatabaseProductName()
               .toLowerCase(Locale.ROOT).contains("oracle"))
            throw new IllegalStateException("Rollback smoke is Oracle-only");

         filename = scalarString(c,
               "SELECT filename FROM xxi.ni_outfiles WHERE int_id=? AND rec_state='0'", sourceId);
         if (filename == null || filename.isBlank())
            throw new IllegalStateException("Reserved source not found or filename is empty: " + sourceId);
         if (scalarCount(c,
               "SELECT COUNT(*) FROM xxi.mi_0600_f WHERE czip_file_name=?", filename) != 0)
            throw new IllegalStateException("Source is already in MI journal: " + sourceId);
      }

      log.warn("MI_0600 ROLLBACK SMOKE START: infId={}, sourceId={}, filename={}",
            infId, sourceId, filename);

      RuntimeException observed = null;
      try
      {
         int processed = repository.capturePendingXml(
               infId, "o.int_id=" + sourceId, 1, 1_048_576L, 2_097_152,
               (req, itm) -> {
                  reqId.set(req);
                  itmId.set(itm);
                  throw new SQLException(MARKER);
               });
         throw new IllegalStateException("Rollback failpoint not reached, processed=" + processed);
      }
      catch (RuntimeException failure)
      {
         if (reqId.get() < 1 || itmId.get() < 1 || !hasMarker(failure))
            throw new IllegalStateException("Rollback smoke did not reach injected failure", failure);
         observed = failure;
      }

      // Independently read committed state AFTER original connection returned.
      try (Connection c = dataSource.getConnection())
      {
         String state = scalarString(c,
               "SELECT rec_state FROM xxi.ni_outfiles WHERE int_id=?", sourceId);
         long requests = scalarCount(c, "SELECT COUNT(*) FROM xxi.mi_req WHERE req_id=?", reqId.get());
         long items = scalarCount(c, "SELECT COUNT(*) FROM xxi.mi_0600 WHERE itm_id=?", itmId.get());
         long files = scalarCount(c, "SELECT COUNT(*) FROM xxi.mi_0600_f WHERE itm_id=?", itmId.get());
         long filenames = scalarCount(c,
               "SELECT COUNT(*) FROM xxi.mi_0600_f WHERE czip_file_name=?", filename);

         if (!"0".equals(state) || requests != 0 || items != 0 || files != 0 || filenames != 0)
            throw new IllegalStateException("MI_0600 ROLLBACK SMOKE FAIL: state=" + state
                  + ", req=" + requests + ", item=" + items + ", files=" + files
                  + ", filenameRows=" + filenames + ", reqId=" + reqId + ", itmId=" + itmId);

         log.info("MI_0600 ROLLBACK SMOKE PASS: infId={}, sourceId={}, reqId={}, itmId={}, " +
                     "REC_STATE=0, MI_REQ=0, MI_0600=0, MI_0600_F=0",
               infId, sourceId, reqId.get(), itmId.get());
      }
   }

   private static String option(ApplicationArguments args, String name, String fallback)
   {
      if (!args.containsOption(name)) return fallback;
      var values = args.getOptionValues(name);
      if (values == null || values.size() != 1 || values.get(0) == null
            || values.get(0).isBlank())
         throw new IllegalArgumentException("Expected one nonblank --" + name);
      return values.get(0);
   }

   private static String requiredOption(ApplicationArguments args, String name)
   {
      if (!args.containsOption(name))
         throw new IllegalArgumentException("Missing --" + name);
      return option(args, name, null);
   }

   private static String scalarString(Connection c, String sql, Object value) throws SQLException
   {
      try (PreparedStatement ps = c.prepareStatement(sql))
      {
         ps.setObject(1, value);
         try (ResultSet rs = ps.executeQuery())
         {
            if (!rs.next())
               throw new SQLException("Expected one row: " + sql);
            String result = rs.getString(1);
            if (rs.next())
               throw new SQLException("More than one row: " + sql);
            return result;
         }
      }
   }

   private static long scalarCount(Connection c, String sql, Object value) throws SQLException
   {
      try (PreparedStatement ps = c.prepareStatement(sql))
      {
         ps.setObject(1, value);
         try (ResultSet rs = ps.executeQuery())
         {
            if (!rs.next())
               throw new SQLException("COUNT query returned no rows");
            long result = rs.getLong(1);
            if (rs.next())
               throw new SQLException("COUNT query returned multiple rows");
            return result;
         }
      }
   }

   private static boolean hasMarker(Throwable failure)
   {
      for (Throwable current = failure; current != null; current = current.getCause())
         if (current.getMessage() != null && current.getMessage().contains(MARKER))
            return true;
      return false;
   }
}
