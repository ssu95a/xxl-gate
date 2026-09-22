package ru.inversion.edo.xxl.mi.internal.handlers.mi_0600;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.mi.internal.InternalRequest;
import ru.inversion.edo.xxl.mi.internal.InternalRequestHandler;
import ru.inversion.edo.xxl.mi.internal.InternalResult;
import ru.inversion.utils.S;
import ru.inversion.utils.converter.TypeConverter;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
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

      ScheduleDayInfo scheduleDayInfo = repo.getSchedule(TypeConverter.convert( request.params().get("requestedDate"), LocalDate.class ));

      final Map<String,Object> responseMap = new HashMap<>();
      responseMap.put( "useSchedule", scheduleDayInfo.useSchedule() );
      responseMap.put( "requestedDate", scheduleDayInfo.requestedDate() );
      if( scheduleDayInfo.useSchedule()  ) {
          responseMap.put( "isWorkday", scheduleDayInfo.isWorkday() );
          if( scheduleDayInfo.isWorkday() && !S.isNullOrEmpty(scheduleDayInfo.weekSchedule() ) )
              responseMap.put("weekSchedule", scheduleDayInfo.weekSchedule() );
      }

      // в MI нужен формат: "working": true,  "allDay": false, "from": "09:00", "to": "18:00"

      return InternalResult.ok( responseMap );
   }
}
