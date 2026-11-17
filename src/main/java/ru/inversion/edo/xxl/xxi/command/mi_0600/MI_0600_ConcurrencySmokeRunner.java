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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DEV-ONLY, one-shot two-worker Oracle SKIP LOCKED probe.
 *
 * Worker A locks one source and waits before creating its journal.
 * Worker B tries a common two-source predicate and, if Oracle 12.1
 * ROWNUM/SKIP LOCKED underfills it, retries explicitly on the free source.
 * Only then A is released; post-commit integrity is checked independently.
 *
 * This is a committing test: use exclusively reserved NON-PRODUCTION rows.
 */
@Component
@Profile("mi0600-concurrency-smoke")
@ConditionalOnProperty(name = "mi0600.concurrency-smoke.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public final class MI_0600_ConcurrencySmokeRunner implements ApplicationRunner
{
   private static final int MAX_FILES = 1;
   private static final long MAX_XML_BYTES = 1_048_576L;
   private static final int MAX_ZIP_BYTES = 2_097_152;
   private static final long TIMEOUT_SECONDS = 25;

   private final MI_0600_Repository repository;
   private final DataSource dataSource;

   @Override
   public void run(ApplicationArguments args) throws Exception
   {
      long firstId = Long.parseLong(required(args, "mi0600.concurrency-smoke.first-id"));
      long secondId = Long.parseLong(required(args, "mi0600.concurrency-smoke.second-id"));
      int infId = Integer.parseInt(option(args, "mi0600.concurrency-smoke.inf-id", "601"));

      if (firstId <= 0 || secondId <= 0 || firstId == secondId)
         throw new IllegalArgumentException("Provide two different positive NI_OUTFILES IDs");
      if (infId != 601 && infId != 603 && infId != 611)
         throw new IllegalArgumentException("Only generic outgoing 601/603/611 are supported");

      String firstName;
      String secondName;
      try (Connection c = dataSource.getConnection())
      {
         if (!c.getMetaData().getDatabaseProductName()
               .toLowerCase(Locale.ROOT).contains("oracle"))
            throw new IllegalStateException("This SKIP LOCKED smoke is Oracle-only");

         firstName = sourceFilename(c, firstId);
         secondName = sourceFilename(c, secondId);
         if (firstName.equals(secondName))
            throw new IllegalStateException("Two source filenames must differ");

         if (count(c, "SELECT COUNT(*) FROM xxi.mi_0600_f WHERE czip_file_name=?", firstName) != 0
               || count(c, "SELECT COUNT(*) FROM xxi.mi_0600_f WHERE czip_file_name=?", secondName) != 0)
            throw new IllegalStateException("A source filename already exists in MI journal");
      }

      log.warn("MI_0600 CONCURRENCY SMOKE START: infId={}, firstId={}, secondId={}",
            infId, firstId, secondId);

      CountDownLatch firstLocked = new CountDownLatch(1);
      CountDownLatch releaseFirst = new CountDownLatch(1);
      AtomicBoolean abort = new AtomicBoolean(false);
      ExecutorService workers = Executors.newFixedThreadPool(2);

      try
      {
         Future<Integer> first = workers.submit(() ->
               repository.capturePendingXml(infId, "o.int_id=" + firstId,
                     MAX_FILES, MAX_XML_BYTES, MAX_ZIP_BYTES,
                     () -> {
                        firstLocked.countDown();
                        if (!releaseFirst.await(TIMEOUT_SECONDS + 10, TimeUnit.SECONDS))
                           throw new SQLException("MI0600_CONCURRENCY_FIRST_LOCK_TIMEOUT");
                        if (abort.get())
                           throw new SQLException("MI0600_CONCURRENCY_ABORT");
                     }));

         if (!firstLocked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            throw new IllegalStateException("Worker A did not reach the locked-source hook");

         log.info("MI_0600 CONCURRENCY: worker A locked source {}, running worker B", firstId);

         Future<SecondOutcome> second = workers.submit(() -> {
            String both = "(o.int_id=" + firstId + " OR o.int_id=" + secondId + ")";
            int common = repository.capturePendingXml(infId, both,
                  MAX_FILES, MAX_XML_BYTES, MAX_ZIP_BYTES);
            if (common > 1)
               throw new IllegalStateException("Worker B exceeded MAX_FILES");

            int fallback = 0;
            if (common == 0)
            {
               // Oracle may first select the locked row for ROWNUM<=1,
               // resulting in a legitimate empty batch.
               log.warn("MI_0600 CONCURRENCY: Oracle common predicate underfilled; retry free source");
               fallback = repository.capturePendingXml(infId, "o.int_id=" + secondId,
                     MAX_FILES, MAX_XML_BYTES, MAX_ZIP_BYTES);
            }
            return new SecondOutcome(common, fallback);
         });

         SecondOutcome outcome = second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
         if (outcome.total() != 1)
            throw new IllegalStateException("Worker B captured " + outcome.total()
                  + " files; expected exactly one free source");

         // With worker A still holding the lock, the independent reader
         // must see A pending and B committed with exactly one journal.
         try (Connection c = dataSource.getConnection())
         {
            checkState(c, firstId, "0");
            checkState(c, secondId, "1");
            checkCount(c, firstName, 0);
            checkCount(c, secondName, 1);
         }

         log.info("MI_0600 CONCURRENCY: B committed while A locked; common={}, fallback={}",
               outcome.common(), outcome.fallback());

         releaseFirst.countDown();
         int firstCount = first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
         if (firstCount != 1)
            throw new IllegalStateException("Worker A captured " + firstCount + " files");

         try (Connection c = dataSource.getConnection())
         {
            checkState(c, firstId, "1");
            checkState(c, secondId, "1");
            checkCount(c, firstName, 1);
            checkCount(c, secondName, 1);
         }

         log.info("MI_0600 CONCURRENCY SMOKE PASS: infId={}, firstId={}, secondId={}, " +
                     "both REC_STATE=1, each filename exactly once in MI_0600_F, " +
                     "worker B common={}, fallback={}",
               infId, firstId, secondId, outcome.common(), outcome.fallback());
      }
      catch (Exception failure)
      {
         abort.set(true);
         log.error("MI_0600 CONCURRENCY SMOKE FAIL: firstId={}, secondId={}", firstId, secondId, failure);
         throw failure;
      }
      finally
      {
         // On failure worker A must roll back, not resume to COMMIT.
         releaseFirst.countDown();
         workers.shutdownNow();
         if (!workers.awaitTermination(10, TimeUnit.SECONDS))
            log.error("MI_0600 CONCURRENCY: worker threads did not finish within 10 seconds");
      }
   }

   private record SecondOutcome(int common, int fallback)
   {
      int total() { return common + fallback; }
   }

   private static String sourceFilename(Connection c, long id) throws SQLException
   {
      try (PreparedStatement ps = c.prepareStatement(
            "SELECT filename FROM xxi.ni_outfiles WHERE int_id=? AND rec_state='0'"))
      {
         ps.setLong(1, id);
         try (ResultSet rs = ps.executeQuery())
         {
            if (!rs.next())
               throw new SQLException("Source not in REC_STATE=0: int_id=" + id);
            String result = rs.getString(1);
            if (result == null || result.isBlank())
               throw new SQLException("Source filename is blank: int_id=" + id);
            if (rs.next())
               throw new SQLException("Source INT_ID is not unique: " + id);
            return result;
         }
      }
   }

   private static void checkState(Connection c, long id, String expected) throws SQLException
   {
      try (PreparedStatement ps = c.prepareStatement(
            "SELECT rec_state FROM xxi.ni_outfiles WHERE int_id=?"))
      {
         ps.setLong(1, id);
         try (ResultSet rs = ps.executeQuery())
         {
            if (!rs.next() || !expected.equals(rs.getString(1)) || rs.next())
               throw new SQLException("Unexpected REC_STATE for int_id=" + id + ", expected " + expected);
         }
      }
   }

   private static void checkCount(Connection c, String filename, int expected)
         throws SQLException
   {
      if (count(c, "SELECT COUNT(*) FROM xxi.mi_0600_f WHERE czip_file_name=?", filename) != expected)
         throw new SQLException("Unexpected MI journal count for filename=" + filename
               + ", expected " + expected);
   }

   private static long count(Connection c, String sql, String value) throws SQLException
   {
      try (PreparedStatement ps = c.prepareStatement(sql))
      {
         ps.setString(1, value);
         try (ResultSet rs = ps.executeQuery())
         {
            if (!rs.next())
               throw new SQLException("Count query did not return a row");
            long result = rs.getLong(1);
            if (rs.next())
               throw new SQLException("Count query returned multiple rows");
            return result;
         }
      }
   }

   private static String required(ApplicationArguments args, String key)
   {
      if (!args.containsOption(key))
         throw new IllegalArgumentException("Missing --" + key);
      return option(args, key, null);
   }

   private static String option(ApplicationArguments args, String key, String fallback)
   {
      if (!args.containsOption(key))
         return fallback;
      var values = args.getOptionValues(key);
      if (values == null || values.size() != 1 || values.get(0) == null
            || values.get(0).isBlank())
         throw new IllegalArgumentException("Expected one nonblank --" + key);
      return values.get(0);
   }
}
