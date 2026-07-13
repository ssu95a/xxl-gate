package ru.inversion.msmev.xxi.availability;

import ru.inversion.msmev.util.Attrs;
import ru.inversion.utils.Checks;
import ru.inversion.utils.S;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Доступность базы XXI.
 */
public record XxiAvailability (

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
   public Map<String, Object> parameters()
   {
      final Attrs attrs = Attrs.create();

      attrs.put( "xxi_available",            available()  );
      attrs.put( "xxi_availability_state",   state.name() );
      attrs.put( "xxi_availability_details", details      );

      attrs.putIfNotNull( "xxi_availability_checked_at", checkedAt  );
      attrs.putIfNotNull( "xxi_availability_changed_at", changedAt);
      attrs.putIfNotNull( "xxi_availability_exception_class", exceptionClass );

      attrs.putIfNotNull( "xxi_availability_sql_state", sqlState);

      return attrs.toMap();
   }

   /** Неизвестное состояние, с него начинается работа  */
   public static XxiAvailability unknown() {
      return new XxiAvailability( XxiAvailabilityState.UNKNOWN, "XXI availability has not been checked yet", null, null, null, null );
   }

}
