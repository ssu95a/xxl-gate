package ru.inversion.edo.xxl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeProfileConfigurationTest
{
   private static PropertySource<?> profile(String name) throws IOException
   {
      List<PropertySource<?>> sources = new YamlPropertySourceLoader()
            .load(name, new ClassPathResource("application-" + name + ".yml"));
      assertEquals(1, sources.size());
      return sources.getFirst();
   }

   @Test
   void oracleCannotAccidentallyReusePostgresDatasourceOrVhost() throws IOException
   {
      PropertySource<?> source = profile("oracle");
      assertEquals("${ORACLE_DB_URL}", source.getProperty("spring.datasource.url"));
      assertEquals("oracle.jdbc.OracleDriver", source.getProperty("spring.datasource.driver-class-name"));
      assertEquals("${ORACLE_RABBITMQ_ADDRESSES}",
            source.getProperty("spring.rabbitmq.addresses"));
      assertEquals("${ORACLE_RABBITMQ_PASSWORD}",
            source.getProperty("spring.rabbitmq.password"));
   }

   @Test
   void postgresUsesIndependentSettings() throws IOException
   {
      PropertySource<?> source = profile("pg");
      assertEquals("${PG_DB_URL}", source.getProperty("spring.datasource.url"));
      assertEquals("org.postgresql.Driver", source.getProperty("spring.datasource.driver-class-name"));
      assertEquals("${PG_RABBITMQ_ADDRESSES}",
            source.getProperty("spring.rabbitmq.addresses"));
      assertEquals("${PG_RABBITMQ_PASSWORD}",
            source.getProperty("spring.rabbitmq.password"));
   }
}
