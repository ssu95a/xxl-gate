package ru.inversion.edo.xxl.mi.internal.handlers.mi_0600;

import java.time.LocalDate;

/** */
public record ScheduleInfo(

   boolean useSchedule,

   boolean isWorkday,

   String  scheduleJson,

   LocalDate dateOn
)
{
   public ScheduleInfo(boolean useSchedule, LocalDate dateOn)
   {
      this(useSchedule, true, null, dateOn);
   }
}
