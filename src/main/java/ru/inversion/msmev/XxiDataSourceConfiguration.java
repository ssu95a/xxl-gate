package ru.inversion.msmev;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataSourceProperties.class)
public class XxiDataSourceConfiguration
{
   /** Штатный Hikari-пул  */
   @Bean(name = "xxiHikariDataSource", destroyMethod = "close")
   @ConfigurationProperties("spring.datasource.hikari")
   public HikariDataSource xxiHikariDataSource( DataSourceProperties properties )
   {
      return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
   }

   /**
    * Основной DataSource приложения.
    *
    * Обычный getConnection() делегируется Hikari.
    * Credential-вариант создаёт отдельное XXI-соединение.
    */
   @Bean(name = "dataSource")
   @Primary
   public DataSource dataSource( @Qualifier("xxiHikariDataSource") HikariDataSource delegate, DataSourceProperties properties )
   {
      return new XxiDataSource( delegate, properties.determineUrl() );
   }
}