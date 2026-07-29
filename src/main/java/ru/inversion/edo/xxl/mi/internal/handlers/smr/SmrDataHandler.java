package ru.inversion.edo.xxl.mi.internal.handlers.smr;

import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.mi.internal.InternalRequest;
import ru.inversion.edo.xxl.mi.internal.InternalRequestHandler;
import ru.inversion.edo.xxl.mi.internal.InternalResult;
import ru.inversion.utils.Checks;
import ru.inversion.utils.U;

import java.sql.SQLException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Возвращает данные из таблицы SMR
 */
@Component
public class SmrDataHandler implements InternalRequestHandler
{
   public static final String QUERY_TYPE = "SMR";


   private final SmrInfoProvider smrProvider;

   /** */
   public SmrDataHandler( SmrInfoProvider smrProvider)
   {
      this.smrProvider = Checks.Require.object( smrProvider, "smrProvider" );
   }

   @Override
   public Set<String> queryTypes()
   {
      return Set.of(QUERY_TYPE);
   }

   @Override
   public InternalResult handle(InternalRequest request) {
      return InternalResult.ok( loadSmr(request) );
   }


   public Map<String, Object> loadSmr(InternalRequest request )
   {
      try {
         return smrProvider.loadSmr();
      }
      catch ( NoSuchElementException e ) {
         throw Errors.miInternalFailed(
              "Database information query returned no row. " + e.getMessage(),
              null,
              U.toMap( "query_type", QUERY_TYPE, "message_id", request.messageId() )
         );

      }
      catch (SQLException e) {
         throw Errors.dbError(
                 "INTERNAL Failed to read XXL database connection information",
                 e,
                 U.toMap( "query_type", QUERY_TYPE, "message_id", request.messageId() )
         );
      }
   }
}