package ru.inversion.msmev.xxi.availability;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <h6>Единый владелец состояния доступности XXI.</h6>
 * <p>Задачи:</p>
 * <ul>
 *    <li>Проверять маркер PUBLIC.XXI_UPGRADE - признак тех перерыва;</li>
 *    <li>Обновлять состояние XXI по расписанию;</li>
 *    <li>Внеочередно проверять состояние после connection-level ошибки;</li>
 *    <li>Хранить последний снимок и диагностику.</li>
 * </ul>
 *
 * <p>Сервис не создаёт и не удаляет маркер, не меняет БД и не определяет retry-политику очереди.</p>
 */
@Slf4j
@Component
public class XxiAvailabilityService {

   static final String TECHNICAL_BREAK_SQL = "select OBJECT_ID from all_objects where owner = 'PUBLIC' and object_name = 'XXI_UPGRADE' and OBJECT_TYPE = 'SYNONYM'";

   private final DataSource dataSource;

   private final long intervalCheckStateMs;

   private final AtomicReference<XxiAvailability> current = new AtomicReference<>( XxiAvailability.unknown() );

   private final ReentrantLock refreshLock = new ReentrantLock();

   public XxiAvailabilityService (
         DataSource dataSource,
         @Value(
            "${xxi.availability.interval-check-state-ms:5000}"
         )
         long intervalCheckStateMs
   )
   {
      this.dataSource           = Objects.requireNonNull( dataSource, "dataSource" );
      this.intervalCheckStateMs = Math.max( 0L, intervalCheckStateMs );
   }

   /**
    * Начальный снимок. Ошибка проверки не мешает запуску приложения:
    * состояние станет CONNECTION_FAILURE или CHECK_FAILURE.
    */
   @PostConstruct
   public void initialize() {
      refresh();
   }

   /** Регулярная проверка на TechnicalBreak. Изначально: каждые 5 минут. */
   @Scheduled(
         initialDelayString =
               "${xxi.availability.initial-delay-ms:300000}",
         fixedDelayString =
               "${xxi.availability.check-delay-ms:300000}"
   )
   public void scheduledRefresh() {
      refresh();
   }

   public XxiAvailability currentState() {
      return current.get();
   }

   public boolean isAvailable() {
      return currentState().available();
   }

   public boolean isTechnicalBreak() {
      return currentState().technicalBreak();
   }

   /** Принудительная проверка новым JDBC-вызовом через DataSource. */
   public XxiAvailability refresh() {
      return refresh(false);
   }

   /**
    * Делает внеочередную проверку только для connection-level ошибки.
    * <p>Защита от шторма: если другой поток недавно уже выполнил
    * проверку, возвращается его снимок.</p>
    */
   public XxiAvailability refreshAfterFailure(Throwable failure)
   {
      if( !isConnectionFailure(failure) )
          return currentState();

      return refresh(true);
   }

   /** Проверяет, относится ли цепочка причин к JDBC connection failure. */
   public boolean isConnectionFailure( Throwable failure )
   {
      Throwable currentCause = failure;

      while( currentCause != null )
      {
         if( currentCause instanceof SQLTransientConnectionException || currentCause instanceof SQLNonTransientConnectionException || currentCause instanceof SQLRecoverableException )
             return true;

         if( currentCause instanceof SQLException sqlException )
         {
             String sqlState = sqlException.getSQLState();

            /* SQLState class 08: connection exception. PG only - con error */
            if( sqlState != null && sqlState.startsWith("08"))
                return true;
         }

         currentCause = currentCause.getCause();
      }

      return false;
   }


   /** */
   private XxiAvailability refresh( boolean suppressRecentDuplicate )
   {
      refreshLock.lock();

      try {

         XxiAvailability previous = current.get();
         OffsetDateTime now = OffsetDateTime.now();

         if( suppressRecentDuplicate && checkedRecently( previous, now ) )
             return previous;

         XxiAvailability actual = queryAvailability( previous, now );
         current.set(actual);

         logState(previous, actual);

         return actual;

      } finally {
         refreshLock.unlock();
      }
   }


   /** */
   private boolean checkedRecently( XxiAvailability availability, OffsetDateTime now )
   {
      if( intervalCheckStateMs == 0L || availability.checkedAt() == null)
          return false;

      long elapsedMs = Math.abs( Duration.between( availability.checkedAt(), now ).toMillis() );

      return elapsedMs < intervalCheckStateMs;
   }


   /** */
   private XxiAvailability queryAvailability( XxiAvailability previous, OffsetDateTime checkedAt )
   {
      try (
         Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(TECHNICAL_BREAK_SQL);
         ResultSet resultSet = statement.executeQuery()
      )
      {
         XxiAvailabilityState state = resultSet.next() ? XxiAvailabilityState.TECHNICAL_BREAK : XxiAvailabilityState.AVAILABLE;

         String details = state == XxiAvailabilityState.TECHNICAL_BREAK ? "PUBLIC.XXI_UPGRADE marker found" : "XXI is available";

         return successSnapshot( previous, state, details, checkedAt );

      } catch (SQLException exception) {
         XxiAvailabilityState state = isConnectionFailure(exception) ? XxiAvailabilityState.CONNECTION_FAILURE : XxiAvailabilityState.CHECK_FAILURE;
         return failureSnapshot( previous, state, exception, checkedAt );
      } catch (RuntimeException exception) {
         return failureSnapshot( previous, XxiAvailabilityState.CHECK_FAILURE, exception, checkedAt );
      }
   }


   /** */
   private XxiAvailability successSnapshot( XxiAvailability previous, XxiAvailabilityState state, String details, OffsetDateTime checkedAt )
   {
      return new XxiAvailability( state, details, checkedAt, stateSince(previous, state, checkedAt), null, null );
   }


   /** */
   private XxiAvailability failureSnapshot ( XxiAvailability previous, XxiAvailabilityState state, Throwable failure, OffsetDateTime checkedAt )
   {
      SQLException sqlException = findSQLException(failure);

      return new XxiAvailability( state, failureDetails(failure), checkedAt, stateSince(previous, state, checkedAt),
            failure == null ? null : failure.getClass().getName(),
            sqlException == null ? null : sqlException.getSQLState()
      );
   }


   /** */
   private OffsetDateTime stateSince(
         XxiAvailability previous,
         XxiAvailabilityState newState,
         OffsetDateTime checkedAt
   )
   {
      if( previous.state() == newState && previous.stateSince() != null)
          return previous.stateSince();

      return checkedAt;
   }

   /** */
   private SQLException findSQLException(Throwable failure) {

      Throwable currentCause = failure;

      while (currentCause != null) {
         if (currentCause instanceof SQLException sqlException)
            return sqlException;

         currentCause = currentCause.getCause();
      }

      return null;
   }


   /** */
   private String failureDetails(Throwable failure) {
      if( failure == null )
          return "Unknown availability check failure";

      String message = failure.getMessage();

      if( message == null || message.isBlank())
          return failure.getClass().getName();

      return message;
   }


   /** */
   public XxiAvailability refreshIfUnavailable()
   {
      XxiAvailability snapshot = currentState();

      if( snapshot.available() || snapshot.technicalBreak() )
         return snapshot;

      return refresh(true);
   }


   /** */
   private void logState( XxiAvailability previous, XxiAvailability actual )
   {
      boolean changed = previous.state() != actual.state();

      if( changed )
      {
         log.warn( "XXI availability changed: previous={}, actual={}, details={}, checkedAt={}, stateSince={}",
               previous.state(),
               actual.state(),
               actual.details(),
               actual.checkedAt(),
               actual.stateSince()
         );
         return;
      }

      if (actual.state()
            == XxiAvailabilityState.CONNECTION_FAILURE
            || actual.state()
            == XxiAvailabilityState.CHECK_FAILURE) {
         log.error(
               "XXI availability check failed: state={}, details={}, "
                     + "exceptionClass={}, sqlState={}, checkedAt={}",
               actual.state(),
               actual.details(),
               actual.exceptionClass(),
               actual.sqlState(),
               actual.checkedAt()
         );
         return;
      }

      log.debug(
            "XXI availability checked: state={}, checkedAt={}",
            actual.state(),
            actual.checkedAt()
      );
   }
}
