package ru.inversion.msmev.mi.internal.handlers;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import ru.inversion.msmev.error.Errors;
import ru.inversion.utils.U;

import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceException;
import javax.sql.DataSource;
import java.io.UncheckedIOException;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SmrInfoProvider
{
   private final DataSource dataSource;

   public SmrInfoProvider(DataSource dataSource)
   {
      this.dataSource = dataSource;
   }

   private static final String SQL =
           "select ( select ccusksiva from vcus where icusnum = smr.ismrcus ) ccusksiva, csmrname, csmraddr, csmrmfo8, csmrbic, ismrinn, idsmr, ismrfil from smr";

   @Cacheable( cacheNames = "smr",cacheManager = "longTermCacheManager", key = "'current'" )
   public Map<String,Object> loadSmr(  ) throws SQLException
   {
      try (
         Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(SQL)
      )
      {
         statement.setQueryTimeout(5);

         try( ResultSet resultSet = statement.executeQuery() )
         {
            if( !resultSet.next() )
            {
               throw new EntityNotFoundException();
            }

            final ResultSetMetaData metaData = resultSet.getMetaData();
            final LinkedHashMap<String,Object> data = new LinkedHashMap<>();

            int nCount = metaData.getColumnCount();

            for( int i = 1; i <= nCount; i++ )
               data.put( metaData.getColumnName(i), resultSet.getObject(i) );

            return data;
         }
      }
   }
}