package ru.inversion.edo.xxl.mi.internal.handlers.mi_0600;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.mi.internal.InternalRequest;
import ru.inversion.edo.xxl.mi.internal.InternalRequestHandler;
import ru.inversion.edo.xxl.mi.internal.InternalResult;
import ru.inversion.utils.U;
import ru.inversion.utils.converter.TypeConverter;

import java.time.LocalDate;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MI_0600_InternalHandler implements InternalRequestHandler {

   final private MI_0600_InternalRepository repo;

   @Override
   public Set<String> queryTypes() {
      return Set.of("mi_0600_get_schedule");
   }

   /** */
   @Override
   public InternalResult handle( InternalRequest request ) {

      ScheduleDayInfo scheduleDayInfo = repo.getSchedule( TypeConverter.convert( request.params().get("resolvedDate"), LocalDate.class ));

//      final Map<String,Object> responseMap = new HashMap<>();
//      responseMap.put( "useSchedule", scheduleDayInfo.useSchedule() );
//      responseMap.put( "requestedDate", scheduleDayInfo.resolvedDate() );
//      if( scheduleDayInfo.useSchedule()  ) {
//          responseMap.put( "isWorkday", scheduleDayInfo.isWorkday() );
//          if( scheduleDayInfo.isWorkday() && !S.isNullOrEmpty(scheduleDayInfo.scheduleJson() ) )
//              responseMap.put("weekSchedule", scheduleDayInfo.scheduleJson() );
//      }

      // в MI нужен формат: "working": true,  "allDay": false, "from": "09:00", "to": "18:00"

      return InternalResult.ok(
         U.toMap(
            "useSchedule", scheduleDayInfo.useSchedule(),
                "working", scheduleDayInfo.isWorkday(),
                 "allDay", scheduleDayInfo.beginTime() == null && scheduleDayInfo.endTime() == null,
                   "from", scheduleDayInfo.beginTime(),
                     "to", scheduleDayInfo.endTime()
         )
      );
   }
}
