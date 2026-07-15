package ru.inversion.msmev.mi.response.item;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.inversion.msmev.util.MdcExecutorService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class MiItemExecutorConfiguration {

   @Bean(
        name = "miResponseItemExecutor",
        destroyMethod = "shutdown"
   )
   public ExecutorService miResponseItemExecutor(
     @Value("${mi.response.item-parallelism:4}")
     int parallelism
   )
   {
      if( parallelism < 1 )
          parallelism = 4;

      ExecutorService delegate
              = Executors.newFixedThreadPool(parallelism, Thread.ofPlatform().name("mi-response-item-", 0).factory() );

      return new MdcExecutorService(delegate);
   }
}