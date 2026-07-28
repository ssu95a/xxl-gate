package ru.inversion.msmev.mi.internal.handlers;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.mi.internal.InternalRequest;
import ru.inversion.msmev.mi.internal.InternalRequestHandler;
import ru.inversion.msmev.mi.internal.InternalResult;
import ru.inversion.utils.Checks;
import ru.inversion.utils.U;

import javax.persistence.EntityNotFoundException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

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
      catch ( EntityNotFoundException enfe ) {
         throw Errors.miInternalFailed(
              "Database information query returned no row",
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