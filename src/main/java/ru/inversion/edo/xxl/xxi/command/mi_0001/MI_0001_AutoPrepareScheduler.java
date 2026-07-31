package ru.inversion.edo.xxl.xxi.command.mi_0001;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MI_0001_AutoPrepareScheduler
{
   private final MI_0001_Repository repository;

   @Scheduled(cron = "${xxl.mi-0001.auto-prepare.cron}")
   public void run()
   {
      repository.submiAutoPrepare();
   }
}