package ru.inversion.msmev;

import ru.inversion.db.session.xxi.PGPseudoConnector;
import ru.inversion.db.session.xxi.XxiConnector;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.net.PasswordAuthentication;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

public final class XxiDataSource implements DataSource
{
   private final DataSource delegate;
   private final DataSource authDataSource;
   private final String jdbcUrl;

   /** */
   public XxiDataSource( DataSource delegate, DataSource authDataSource, String jdbcUrl )
   {
      this.delegate       = Objects.requireNonNull( delegate, "delegate" );
      this.authDataSource = Objects.requireNonNull( authDataSource, "authDataSource" );

      if( jdbcUrl == null || jdbcUrl.isBlank() )
          throw new IllegalArgumentException( "jdbcUrl is empty" );

      this.jdbcUrl = jdbcUrl;
   }

   /** Штатное соединение XXL через Hikari. */
   @Override
   public Connection getConnection( ) throws SQLException
   {
      return delegate.getConnection();
   }


   /** Соединение под переданным XXI-пользователем. */
   @Override
   public Connection getConnection( String username, String password ) throws SQLException
   {
      if( username == null || username.isBlank() )
          throw new SQLException( "XXI username is empty" );

      if( password == null )
          throw new SQLException( "XXI password is null" );

      Properties properties = new Properties();

      PasswordAuthentication requested = new PasswordAuthentication( username, password.toCharArray() );
      PasswordAuthentication databaseAuthentication;

      /*
       * Важно: здесь используется отдельный ограниченный
       * технический пользователь, не основной Hikari user.
       */
      try( Connection technicalConnection = authDataSource.getConnection() )
      {
         if(!technicalConnection.getAutoCommit() )
             technicalConnection.setAutoCommit(true);

         databaseAuthentication = authenticate( technicalConnection, requested, properties );
      }

      properties.setProperty( "user", databaseAuthentication.getUserName() );
      properties.setProperty( "password", String.valueOf( databaseAuthentication.getPassword() ) );

      try
      {
         return createUserConnection(properties);
      }
      finally
      {
         properties.remove("password");
         properties.remove("user");
      }
   }


   /** */
   private PasswordAuthentication authenticate( Connection techConnection, PasswordAuthentication authentication, Properties properties )
           throws SQLException
   {
      String productName = techConnection .getMetaData() .getDatabaseProductName();
      String normalizedProductName = productName == null ? "" : productName.toLowerCase(Locale.ROOT);

      if( normalizedProductName.contains("postgresql") )
         return PGPseudoConnector.serverSideLogin( techConnection, authentication, properties );

      if( normalizedProductName.contains("oracle") )
         return XxiConnector.serverSideLogin( techConnection, authentication, properties );

      throw new SQLException( "Unsupported XXI database product: " + productName );
   }

   /** */
   private Connection createUserConnection( Properties properties ) throws SQLException
   {
      Connection connection = DriverManager.getConnection( jdbcUrl, properties );

      try
      {
         connection.setAutoCommit(false);
         return connection;
      }
      catch( SQLException exception )
      {
         try
         {
            connection.close();
         }
         catch( SQLException closeException ) {
            exception.addSuppressed( closeException );
         }

         throw exception;
      }
   }

   @Override
   public PrintWriter getLogWriter() throws SQLException
   {
      return delegate.getLogWriter();
   }

   @Override
   public void setLogWriter( PrintWriter writer ) throws SQLException
   {
      delegate.setLogWriter(writer);
   }

   @Override
   public void setLoginTimeout( int seconds ) throws SQLException
   {
      delegate.setLoginTimeout(seconds);
   }

   @Override
   public int getLoginTimeout() throws SQLException
   {
      return delegate.getLoginTimeout();
   }

   @Override
   public Logger getParentLogger() throws SQLFeatureNotSupportedException
   {
      return delegate.getParentLogger();
   }

   @Override
   public <T> T unwrap( Class<T> type ) throws SQLException
   {
      if( type.isInstance(this) )
         return type.cast(this);

      return delegate.unwrap(type);
   }

   @Override
   public boolean isWrapperFor( Class<?> type ) throws SQLException
   {
      return type.isInstance(this) || delegate.isWrapperFor(type);
   }
}