package ru.inversion.edo.xxl.mi.internal.handlers.lic;

import org.springframework.stereotype.Repository;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.U;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Repository
public class LicensesCheckRepository {

   private final XxiRepositoryExecutor db;

   public LicensesCheckRepository( XxiRepositoryExecutor db ) {
      this.db = db;
   }

   /** */
   private List<Integer> readAvailableLicenses( TaskContext tc, List<Integer> licensesList )
   {
      try( PreparedStatement ps = tc.getConnection().prepareStatement
           ("SELECT array_agg(lics.num) \n" +
           "  FROM unnest(?) AS lics(num)\n" +
           " WHERE QTRN.Get_Bank_Name(issc => lics.num) IS NOT NULL")
      )
      {
         ps.setArray( 1, ps.getConnection().createArrayOf("int4", licensesList.toArray() ));

         try( ResultSet rs  = ps.executeQuery() ) {

               if( !rs.next() )
                   throw new NoSuchElementException("lic list is empty");

              Array a = rs.getArray(1);

              return Arrays.asList( (Integer[])a.getArray() );
         }

      }
      catch( SQLException e ) {
         throw Errors.dbError("LICENSES_CHECK_REPOSITORY error on read lic list", e, U.toMap( "repository", getClass().getName() ) );
      }

      /*
      SQLDataSet<Integer> dsNums = null;
      try
      {
         dsNums = new SQLDataSet<>( tc, Integer.class )
            .queryAllRows()
            .sql("SELECT array_agg(lics.num) \n" +
                        "  FROM unnest(ARRAY[?]) AS lics(num)\n" +
                        " WHERE QTRN.Get_Bank_Name(issc => lics.num) IS NOT NULL")
            .rowMapper( (rs,n) -> rs.getInt(1) )
                 .set( 0, licensesList )
            .execute();
      }
      catch( DataSetException e ) {
         throw Errors.dbError("LICENSES_CHECK_REPOSITORY error on read lic list", e, U.toMap( "repository", getClass().getName() ) );
      }
      */

      //return List.copyOf( dsNums.getRows() );
   }

   /** */
   public List<Integer> getAvailableLicenses( List<Integer> licensesList )
   {
      return db.execute(
              "getAvailableLicenses",
              Map.of(),
              tc -> readAvailableLicenses(tc, licensesList)
      );
   }
}
