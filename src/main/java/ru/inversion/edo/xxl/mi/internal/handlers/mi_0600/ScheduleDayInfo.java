package ru.inversion.edo.xxl.mi.internal.handlers.mi_0600;

import java.time.LocalDate;
import java.time.LocalTime;

/** */
public record ScheduleDayInfo(

   boolean useSchedule,

   boolean isWorkday,

   String weekSchedule,

   LocalTime beginTime,

   LocalTime endTime,

   LocalDate resolvedDate

   )
{
   public ScheduleDayInfo( boolean useSchedule, LocalDate requestedDate )
   {
      this(useSchedule, true, null, null, null, requestedDate);
   }
}
