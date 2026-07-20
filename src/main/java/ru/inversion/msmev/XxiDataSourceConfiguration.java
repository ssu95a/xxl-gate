package ru.inversion.msmev;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class XxiDataSourceConfiguration
{
   /** */
   @Bean
   public XxiDataSource xxiCredentialDataSource( DataSource dataSource, DataSourceProperties dataSourceProperties )
   {
      return new XxiDataSource( dataSource, dataSourceProperties.determineUrl() );
   }
}