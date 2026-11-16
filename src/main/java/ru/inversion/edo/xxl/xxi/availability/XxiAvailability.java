package ru.inversion.edo.xxl.xxi.availability;

import ru.inversion.edo.xxl.util.Attrs;
import ru.inversion.utils.Checks;
import ru.inversion.utils.S;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Доступность базы XXI.
 */
public record XxiAvailability(

        XxiAvailabilityState state,
        String details,
        OffsetDateTime checkedAt,
        OffsetDateTime changedAt,
        String exceptionClass,
        String sqlState,

        Throwable cause
)
{
   public XxiAvailability
   {
      state   = Checks.Require.object(state, "state");
      details = details == null ? S.EMPTY_STRING : details;
   }

   public boolean available()
   {
      return state.available();
   }

   public boolean technicalBreak()
   {
      return state == XxiAvailabilityState.TECHNICAL_BREAK;
   }

   public Map<String, Object> parameters()
   {
      final Attrs attrs = Attrs.create();

      attrs.put("xxi_available", available());
      attrs.put("xxi_availability_state", state.name());
      attrs.put("xxi_availability_details", details);

      attrs.putIfNotNull("xxi_availability_checked_at", checkedAt);
      attrs.putIfNotNull("xxi_availability_changed_at", changedAt);
      attrs.putIfNotNull("xxi_availability_exception_class", exceptionClass);
      attrs.putIfNotNull("xxi_availability_sql_state", sqlState);

      return attrs.toMap();
   }

   public static XxiAvailability unknown()
   {
      return new XxiAvailability(
              XxiAvailabilityState.UNKNOWN,
              "XXI availability has not been checked yet",
              null,
              null,
              null,
              null,
              null
      );
   }
}