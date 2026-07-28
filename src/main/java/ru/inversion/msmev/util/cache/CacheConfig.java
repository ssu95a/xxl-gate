package ru.inversion.msmev.util.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

   // Долгоживущий кэш
   @Bean
   public CacheManager longTermCacheManager( )
   {
      CaffeineCacheManager cm = new CaffeineCacheManager("smr");
      cm.setCaffeine(Caffeine.newBuilder().maximumSize(1).expireAfterWrite(23, TimeUnit.HOURS) );
      return cm;
   }

   // Оперативный кэш
   @Bean
   @Primary  // будет использоваться по умолчанию, если не указан cacheManager
   public CacheManager operationalCacheManager()
   {
      CaffeineCacheManager cm = new CaffeineCacheManager("tempData");
      cm.setCaffeine(Caffeine.newBuilder().maximumSize(1_000).expireAfterWrite(2, TimeUnit.MINUTES));
      return cm;
   }
}