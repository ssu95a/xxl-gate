package ru.inversion.msmev.xxi.availability;

import ru.inversion.utils.Checks;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Доступность XXI.
 *
 * @param state          текущее состояние
 * @param details        диагностическое описание
 * @param checkedAt      время последней проверки
 * @param stateSince     время перехода в текущее состояние
 * @param exceptionClass класс ошибки последней проверки
 * @param sqlState       SQLState последней JDBC-ошибки
 */
public record XxiAvailability(

   XxiAvailabilityState state,

   String details,

   OffsetDateTime checkedAt,
   OffsetDateTime stateSince,

   String exceptionClass,
   String sqlState
)
{
   /** */
   public XxiAvailability
   {
      state   = Checks.Require.object( state, "state" );
      details = details == null ? S.EMPTY_STRING : details;
   }

   /** Неизвестное состояние */
   public static XxiAvailability unknown() {
      return new XxiAvailability( XxiAvailabilityState.UNKNOWN, "XXI availability has not been checked yet", null, null, null, null );
   }


   /** XXI в достпупе */
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
      if( stateSince != null )
          result.put( "xxi_availability_state_since", stateSince);

      result.put( "xxi_availability_exception_class", exceptionClass);
      result.put( "xxi_availability_sql_state", sqlState);

      return result;
   }
}
