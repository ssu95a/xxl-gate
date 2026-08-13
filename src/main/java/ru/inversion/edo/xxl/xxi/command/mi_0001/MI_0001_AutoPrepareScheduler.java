package ru.inversion.edo.xxl.xxi.command.mi_0001;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.error.Errors;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class MI_0001_AutoPrepareScheduler
{
   private final MI_0001_Repository repository;

   @Scheduled(
      cron = "${xxl.mi-0001.auto-prepare.cron:0 30 2 * * *}"
   )
   public void run()
   {
      long result = repository.submitAutoPrepare();

      if( result == 0 )
         throw Errors.internal( "MI_0001 submit_Auto_Prepare returned zero job id", null, Map.of() );

      long jobId = Math.abs(result);

      if( result > 0 )
         log.info( "MI_0001 auto prepare submitted: jobId={}", jobId );
      else
         log.info( "MI_0001 auto prepare already submitted or running: jobId={}", jobId );
   }
}
