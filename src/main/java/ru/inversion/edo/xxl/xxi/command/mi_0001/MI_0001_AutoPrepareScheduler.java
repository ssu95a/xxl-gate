package ru.inversion.edo.xxl.xxi.command.mi_0001;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MI_0001_AutoPrepareScheduler
{
   private final MI_0001_Repository repository;

   @Scheduled(cron = "${xxl.mi-0001.auto-prepare.cron}")
   public void run()
   {
      long result = repository.submitAutoPrepare();
      long jobId = Math.abs(result);

      if(result > 0)
         log.info("MI_0001 auto prepare submitted: jobId={}", jobId);
      else
         log.info("MI_0001 auto prepare already submitted or running: jobId={}", jobId);
   }
}