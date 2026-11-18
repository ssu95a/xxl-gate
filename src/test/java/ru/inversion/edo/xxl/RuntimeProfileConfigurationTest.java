package ru.inversion.edo.xxl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Внешняя конфигурация двух контуров не должна оказаться внутри JAR. */
class RuntimeProfileConfigurationTest
{
   private static PropertySource<?> external(String file) throws IOException
   {
      List<PropertySource<?>> sources = new YamlPropertySourceLoader()
            .load(file, new FileSystemResource(file));
      assertEquals(1, sources.size(), file);
      return sources.getFirst();
   }

   @Test
   void onlyCommonConfigurationIsPackagedInClasspath()
   {
      assertTrue(new ClassPathResource("application.yml").exists());
      assertFalse(new ClassPathResource("application-oracle.yml").exists());
      assertFalse(new ClassPathResource("application-pg.yml").exists());
   }

   @Test
   void baseExternalConfigImportsTheSelectedFileOnly() throws IOException
   {
      List<PropertySource<?>> documents = new YamlPropertySourceLoader()
            .load("xxlgate", new FileSystemResource("application-xxlgate.yml"));

      assertEquals(3, documents.size());
      assertEquals("oracle",
            documents.get(1).getProperty("spring.config.activate.on-profile"));
      assertEquals("file:./application-xxlgate-oracle.yml",
            documents.get(1).getProperty("spring.config.import"));
      assertEquals("pg",
            documents.get(2).getProperty("spring.config.activate.on-profile"));
      assertEquals("file:./application-xxlgate-pg.yml",
            documents.get(2).getProperty("spring.config.import"));
   }

   @Test
   void oracleUsesOwnDbAndBusVariables() throws IOException
   {
      PropertySource<?> source = external("application-xxlgate-oracle.yml");
      assertEquals("${ORACLE_DB_URL}", source.getProperty("spring.datasource.url"));
      assertEquals("oracle.jdbc.OracleDriver",
            source.getProperty("spring.datasource.driver-class-name"));
      assertEquals("${ORACLE_RABBITMQ_ADDRESSES}",
            source.getProperty("spring.rabbitmq.addresses"));
      assertEquals("${ORACLE_RABBITMQ_PASSWORD}",
            source.getProperty("spring.rabbitmq.password"));
   }

   @Test
   void postgresUsesOwnDbAndBusVariables() throws IOException
   {
      PropertySource<?> source = external("application-xxlgate-pg.yml");
      assertEquals("${PG_DB_URL}", source.getProperty("spring.datasource.url"));
      assertEquals("org.postgresql.Driver",
            source.getProperty("spring.datasource.driver-class-name"));
      assertEquals("${PG_RABBITMQ_ADDRESSES}",
            source.getProperty("spring.rabbitmq.addresses"));
      assertEquals("${PG_RABBITMQ_PASSWORD}",
            source.getProperty("spring.rabbitmq.password"));
   }
}
