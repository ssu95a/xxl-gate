package ru.inversion.msmev;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import ru.inversion.db.session.xxi.ConnectorBase;
import ru.inversion.utils.security.PasswordAuthenticationCleaner;

import javax.sql.DataSource;
import java.net.PasswordAuthentication;

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
    * <h6>Непуленный DataSource встроенного технического пользователя XXI</h6>
    * <p>
    * Используется для поддержки механизма псевдосоединения.
    */
   @Bean(name = "xxiAuthenticationDataSource")
   public DataSource xxiAuthenticationDataSource( DataSourceProperties properties )
   {
      final PasswordAuthentication authentication = ConnectorBase.getTechUserAuth();

      try( PasswordAuthenticationCleaner ignored = new PasswordAuthenticationCleaner(authentication) )
      {
         DriverManagerDataSource dataSource = new DriverManagerDataSource();

         dataSource.setUrl( properties.determineUrl() );
         dataSource.setDriverClassName( properties.determineDriverClassName() );
         dataSource.setUsername( authentication.getUserName() );
         dataSource.setPassword( String.valueOf(authentication.getPassword()) );

         return dataSource;
      }
   }

   /**
    * <h6>Основной DataSource приложения.</h6>
    * <p>
    * <ul>
    * <li>Обычный getConnection() делегируется Hikari.
    * <li>Credential-вариант создаёт отдельное XXI-соединение.
    * </ul>
    */
   @Bean(name = "dataSource")
   @Primary
   public DataSource dataSource(
           @Qualifier("xxiHikariDataSource")
           HikariDataSource delegate,
           @Qualifier("xxiAuthenticationDataSource")
           DataSource authDataSource,
           DataSourceProperties properties
   )
   {
      return new XxiDataSource( delegate,authDataSource, properties.determineUrl() );
   }
}