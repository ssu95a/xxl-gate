package ru.inversion.edo.xxl.mi.internal.handlers.mi_0600;

import java.time.LocalDate;
import java.time.LocalTime;

/** */
public record ScheduleDayInfo(

   boolean useSchedule,

   boolean isWorkday,

   String weekSchedule,

   LocalDate requestedDate,

   LocalTime beginTime,

   LocalTime endTime
)
{
   public ScheduleDayInfo( boolean useSchedule, LocalDate requestedDate )
   {
      this(useSchedule, true, null, requestedDate, null, null);
   }
}
