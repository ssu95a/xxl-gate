package ru.inversion.edo.xxl.xxi.command.mi_0600;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import ru.inversion.edo.xxl.xxi.repo.InfRole;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Планировщик MI_0600.
 *
 * Интервал запуска хранится в MI_PRP (WSP=600, SCAN_DELAY).
 * Настройки отдельных видов сведений читаются через InfConfig из БД.
 * Исходящий поток не использует SEND_DIR и временные ZIP-файлы.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class MI_0600_Scheduler implements SchedulingConfigurer
{
   private static final Set<Integer> OUTGOING_INF_IDS = Set.of(601, 603, 611);
   private static final Duration DEFAULT_SCAN_DELAY = Duration.ofMinutes(5);
   private static final int MAX_SAFE_ZIP_BYTES = 128 * 1024 * 1024;

   private final MI_0600_Repository repository;

   // Предупреждение о ненастроенном маршруте выводим один раз до изменения состояния.
   private final Set<Integer> warnedMissingRoutes = ConcurrentHashMap.newKeySet();
   private volatile Duration lastScanDelay = DEFAULT_SCAN_DELAY;

   @Override
   public void configureTasks(ScheduledTaskRegistrar registrar)
   {
      registrar.addTriggerTask(
            this::run,
            context -> {
               Duration delay = resolveScanDelay();
               Instant lastCompletion = context.lastCompletion();
               return (lastCompletion == null ? Instant.now() : lastCompletion).plus(delay);
            }
      );
   }

   /**
    * Сначала отправляем уже зарегистрированные запросы, затем регистрируем
    * новую порцию XML. Новые запросы отправляются на следующем запуске.
    */
   void run()
   {
      sendPending();
      capturePending();
   }

   private Duration resolveScanDelay()
   {
      try
      {
         Duration configured = repository.getScanDelay();
         if( configured == null || configured.isZero() || configured.isNegative() )
         {
            log.warn("MI_0600: некорректный SCAN_DELAY={}, используется предыдущий интервал={}",
                  configured, lastScanDelay);
            return lastScanDelay;
         }
         lastScanDelay = configured;
         return configured;
      }
      catch( Exception e )
      {
         log.warn("MI_0600: не удалось прочитать SCAN_DELAY, повтор через {}", lastScanDelay, e);
         return lastScanDelay;
      }
   }

   private void sendPending()
   {
      try
      {
         repository.sendPending();
      }
      catch( Exception e )
      {
         log.warn("MI_0600: sendPending завершился ошибкой, регистрация XML продолжится", e);
      }
   }

   private void capturePending()
   {
      final List<InfConfig> configs;
      try
      {
         configs = repository.loadInfConfigs(InfRole.Initiator);
      }
      catch( Exception e )
      {
         log.error("MI_0600: не удалось получить настройки видов сведений из БД", e);
         return;
      }

      for( InfConfig config : configs )
      {
         if( config == null || !OUTGOING_INF_IDS.contains(config.infId()) )
            continue;

         if( !config.enabled() )
         {
            warnedMissingRoutes.remove(config.infId());
            continue;
         }

         String route = config.capturePredicate();
         if( route == null || route.isBlank() )
         {
            if( warnedMissingRoutes.add(config.infId()) )
               log.warn("MI_0600: для inf_id={} не задан CAPTURE_PREDICATE, захват отключён",
                     config.infId());
            continue;
         }

         warnedMissingRoutes.remove(config.infId());

         try
         {
            validateConfig(config);
            captureInf(config, route);
         }
         catch( Exception e )
         {
            // Сохраняем исходную SQLException как причину ошибки,
            // чтобы диагностика PostgreSQL оставалась доступной в логах.
            log.error("MI_0600: ошибка регистрации XML: inf_id={}, CAPTURE_PREDICATE={}",
                  config.infId(), route, e);
         }
      }
   }

   /**
    * Проверяем только ресурсные ограничения обработки batch.
    * SQL-предикат администрируется в БД и выполняется PostgreSQL без
    * предварительного разбора или переписывания на стороне Java.
    */
   private void validateConfig(InfConfig config)
   {
      if( config.captureMaxFiles() < 1 || config.captureMaxFiles() > 1000 ||
            config.captureMaxXmlBytes() < 1 ||
            config.captureMaxZipBytes() < 1 || config.captureMaxZipBytes() > MAX_SAFE_ZIP_BYTES ||
            config.captureMaxBatches() < 1 || config.captureMaxBatches() > 1000 )
         throw new IllegalArgumentException("Недопустимые ограничения batch MI_0600");
   }

   private void captureInf(InfConfig config, String predicate)
   {
      int capturedFiles = 0;
      int capturedBatches = 0;

      for( int i = 0; i < config.captureMaxBatches(); i++ )
      {
         // Каждый вызов открывает свой TaskContext: SELECT FOR UPDATE SKIP LOCKED,
         // ZIP, регистрация MI_REQ, rec_state=1, затем единый COMMIT.
         int count = repository.capturePendingXml(
               config.infId(),
               predicate,
               config.captureMaxFiles(),
               config.captureMaxXmlBytes(),
               config.captureMaxZipBytes());

         if( count == 0 )
            break;

         capturedBatches++;
         capturedFiles += count;
      }

      if( capturedBatches > 0 )
         log.info("MI_0600: зарегистрировано XML: inf_id={}, пакетов={}, файлов={}",
               config.infId(), capturedBatches, capturedFiles);
   }
}
