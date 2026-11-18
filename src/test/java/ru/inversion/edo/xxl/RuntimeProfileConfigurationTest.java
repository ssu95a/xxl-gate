package ru.inversion.edo.xxl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
   void sharedConfigDoesNotProvideAnyConnectionFallback() throws IOException
   {
      List<PropertySource<?>> docs = new YamlPropertySourceLoader()
            .load("xxlgate", new FileSystemResource("application-xxlgate.yml"));

      PropertySource<?> common = docs.getFirst();
      for (String key : new String[] {
            "spring.datasource.url",
            "spring.datasource.username",
            "spring.datasource.password",
            "spring.datasource.driver-class-name",
            "spring.rabbitmq.addresses",
            "spring.rabbitmq.username",
            "spring.rabbitmq.password"
      }) {
         assertNull(common.getProperty(key), "Connection leaked to shared config: " + key);
      }

      assertEquals(12, common.getProperty("spring.datasource.hikari.maximum-pool-size"));
      assertEquals(2, common.getProperty("spring.rabbitmq.listener.simple.concurrency"));
   }

   @Test
   void dockerUsesExplicitPgOverlayAndPreservesExistingCiNames() throws IOException
   {
      PropertySource<?> compose = external("compose.yaml");
      assertEquals("xxlgate,pg,docker",
            compose.getProperty("services.xxl.environment.SPRING_PROFILES_ACTIVE"));
      assertEquals("${XXL_DB_URL}",
            compose.getProperty("services.xxl.environment.PG_DB_URL"));
      assertEquals("${XXL_DB_USER}",
            compose.getProperty("services.xxl.environment.PG_DB_USER"));
      assertEquals("${XXL_DB_PASSWORD}",
            compose.getProperty("services.xxl.environment.PG_DB_PASSWORD"));
      assertEquals("${XXL_CBSBUS_PASSWORD}",
            compose.getProperty("services.xxl.environment.PG_RABBITMQ_PASSWORD"));

      String dockerfile = Files.readString(Path.of("Dockerfile"));
      assertTrue(dockerfile.contains("COPY application-xxlgate-pg.yml ./"));
      assertFalse(dockerfile.contains("COPY application-xxlgate-oracle.yml ./"));
   }

   @Test
   void oracleUsesOwnDbAndBusVariables() throws IOException
   {
      PropertySource<?> source = external("application-xxlgate-oracle.yml");
      assertEquals("optional:file:./application-xxlgate-oracle.local.yml",
            source.getProperty("spring.config.import"));
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
