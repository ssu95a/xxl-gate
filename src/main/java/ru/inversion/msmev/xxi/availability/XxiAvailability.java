package ru.inversion.msmev.xxi.availability;

import ru.inversion.utils.Checks;
import ru.inversion.utils.S;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Доступность базы XXI.
 *
 * @param state          текущее состояние
 * @param details        диагностическое описание
 * @param checkedAt      время последней проверки
 * @param changedAt     время перехода в текущее состояние
 * @param exceptionClass класс ошибки последней проверки
 * @param sqlState       SQLState последней JDBC-ошибки
 */
public record XxiAvailability(

   /* Текущее состояние */
   XxiAvailabilityState state,

   /* Диагностическое описание состояния */
   String details,

   /* Время последней проверки */
   OffsetDateTime checkedAt,

   /* Время перехода в текущее состояние */
   OffsetDateTime changedAt,

   /* Класс ошибки последней проверки */
   String exceptionClass,

   /* SQLState последней JDBC-ошибки */
   String sqlState
)
{
   /** */
   public XxiAvailability
   {
      state   = Checks.Require.object( state, "state" );
      details = details == null ? S.EMPTY_STRING : details;
   }


   /** XXI в доступе */
   public boolean available() {
      return state.available();
   }

   /** XXI в техническом перерыве */
   public boolean technicalBreak() {
      return state == XxiAvailabilityState.TECHNICAL_BREAK;
   }

   /** Для логов и XXLException. */
   public Map<String, Object> parameters() {

      final Map<String, Object> result = new LinkedHashMap<>();

      result.put( "xxi_available",            available()  );
      result.put( "xxi_availability_state",   state.name() );
      result.put( "xxi_availability_details", details      );

      if( checkedAt != null )
          result.put( "xxi_availability_checked_at", checkedAt  );

      if( changedAt != null )
          result.put( "xxi_availability_changed_at", changedAt);

      if(!S.isNullOrEmpty(exceptionClass) )
          result.put( "xxi_availability_exception_class", exceptionClass );

      if(!S.isNullOrEmpty(sqlState) )
          result.put( "xxi_availability_sql_state", sqlState);

      return result;
   }

   /** Неизвестное состояние, с него начинается работа  */
   public static XxiAvailability unknown() {
      return new XxiAvailability( XxiAvailabilityState.UNKNOWN, "XXI availability has not been checked yet", null, null, null, null );
   }

}
