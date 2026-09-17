package ru.inversion.edo.xxl.mi.internal.handlers.mi_0600;

import org.springframework.stereotype.Repository;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
public class MI_0600_InternalRepository {

   private final XxiRepositoryExecutor db;

   public MI_0600_InternalRepository( XxiRepositoryExecutor db ) {
      this.db = db;
   }

   /** */
   public Optional<String> getSchedule( )
   {
      return db.execute( "getSchedule", Map.of(), this::readSchedule );
   }

   /** */
   private Optional<String> readSchedule( TaskContext tc )
   {
      try( PreparedStatement ps = tc.getConnection().prepareStatement("select mi_0600_api.get_schedule()::text") )
      {
         try( ResultSet rs  = ps.executeQuery() ) {

            if( !rs.next() )
                return Optional.empty();

            String s = rs.getString(1);

            if( S.isNullOrEmpty(s) || rs.wasNull() )
                return Optional.empty();

            return Optional.of(s);
         }
      }
      catch( SQLException e ) {
         throw Errors.dbError( "readSchedule", e, U.toMap( "repository", getClass().getName() ) );
      }
   }
}
