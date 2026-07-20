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
   private final String jdbcUrl;

   /** */
   public XxiDataSource( DataSource delegate, String jdbcUrl )
   {
      this.delegate = Objects.requireNonNull( delegate, "delegate" );
      this.jdbcUrl  = Objects.requireNonNull( jdbcUrl, "jdbcUrl" );
   }


   @Override
   public Connection getConnection( ) throws SQLException
   {
      return delegate.getConnection();
   }

   @Override
   public Connection getConnection( String username, String password ) throws SQLException
   {
      final Properties properties = new Properties();

      try( Connection technicalConnection = delegate.getConnection() )
      {
         PasswordAuthentication authentication = authenticate( technicalConnection, new PasswordAuthentication( username, password.toCharArray()), properties );

         properties.setProperty( "user", authentication.getUserName() );
         properties.setProperty( "password", String.valueOf( authentication.getPassword() ) );

         Connection connection = DriverManager.getConnection( jdbcUrl, properties );
         connection.setAutoCommit(false);

         return connection;
      }
   }

   /** */
   private PasswordAuthentication authenticate( Connection technicalConnection, PasswordAuthentication authentication, Properties properties )
           throws SQLException
   {
      String productName = technicalConnection.getMetaData().getDatabaseProductName();

      if( productName != null && productName.toLowerCase(Locale.ROOT).contains("postgresql") )
         return PGPseudoConnector.serverSideLogin( technicalConnection, authentication, properties );

      if( productName != null && productName.toLowerCase(Locale.ROOT) .contains("oracle") )
         return XxiConnector.serverSideLogin( technicalConnection, authentication, properties );

      throw new SQLException( "Unsupported XXI database product: " + productName );
   }

   @Override
   public PrintWriter getLogWriter() throws SQLException {
      return delegate.getLogWriter();
   }

   @Override
   public void setLogWriter(PrintWriter out) throws SQLException {
      delegate.setLogWriter(out);
   }

   @Override
   public void setLoginTimeout(int seconds) throws SQLException {
      delegate.setLoginTimeout(seconds);
   }

   @Override
   public int getLoginTimeout() throws SQLException {
      return delegate.getLoginTimeout();
   }

   @Override
   public Logger getParentLogger() throws SQLFeatureNotSupportedException {
      return delegate.getParentLogger();
   }

   @Override
   public <T> T unwrap(Class<T> iface) throws SQLException {
      return delegate.unwrap(iface);
   }

   @Override
   public boolean isWrapperFor(Class<?> iface) throws SQLException {
      return delegate.isWrapperFor(iface);
   }
}