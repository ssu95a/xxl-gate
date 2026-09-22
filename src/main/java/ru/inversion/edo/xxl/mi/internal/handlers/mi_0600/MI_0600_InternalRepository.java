package ru.inversion.edo.xxl.mi.internal.handlers.mi_0600;

import org.springframework.stereotype.Repository;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.U;

import java.net.URL;
import java.time.LocalDate;
import java.util.*;

@Repository
public class MI_0600_InternalRepository {

   private static final URL DEF_XML = MI_0600_InternalRepository.class.getResource("plsql/def.xml");

   private final XxiRepositoryExecutor db;

   public MI_0600_InternalRepository( XxiRepositoryExecutor db ) {
      this.db = db;
   }

   /** */
   public ScheduleDayInfo getSchedule(LocalDate dateOn )
   {
      return db.execute("getSchedule", Map.of(), tc -> readSchedule( tc, dateOn ));
   }

   /** */
   private ScheduleDayInfo readSchedule(TaskContext tc, LocalDate dateOn )
   {
      Integer retVal   = null;
      String  retInf   = null;

      try( IDataCall call = SQLCallBuilder.NEW(tc).url(DEF_XML).name("mi_0600_api.get_schedule_config").build().set("on_date", dateOn).execute() )
      {
         retVal = call.getReturnValue();
         retInf = call.get("ret_info" );

         if( retVal == null || retVal != 0 )
             throw Errors.xxiCallFailed( "mi_0600_api.get_schedule_config", 0L, U.nvl( retVal, -1), retInf, null );

         Boolean useSchedule = call.get("use_schedule");

         if( useSchedule == null )
             throw Errors.xxiCallFailed( "mi_0600_api.get_schedule_config", 0L, retVal, "out parameter 'use_schedule' is null", null );

         if( useSchedule )
            return new ScheduleDayInfo( useSchedule, call.get("is_workday"), call.get("schedule_json"), call.get("begin_time"), call.get("end_time"), call.get("resolved_date") );
         else
            return new ScheduleDayInfo( useSchedule, call.get("resolved_date") );
      }
   }
}
