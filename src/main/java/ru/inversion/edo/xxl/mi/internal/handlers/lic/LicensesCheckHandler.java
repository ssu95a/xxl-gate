package ru.inversion.edo.xxl.mi.internal.handlers.lic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.mi.internal.InternalRequest;
import ru.inversion.edo.xxl.mi.internal.InternalRequestHandler;
import ru.inversion.edo.xxl.mi.internal.InternalResult;
import ru.inversion.utils.U;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** */
@Component
@RequiredArgsConstructor
public class LicensesCheckHandler implements InternalRequestHandler {

   final private static List<Integer> LICENCES_LIST =
      Arrays.asList(463,464,398,465,453,332,356,478,522,451,415,78,140,53,60,229,336,357,438,419,461,462,318);

   final private LicensesCheckRepository repo;

   /** */
   @Override
   public Set<String> queryTypes() {
      return Set.of( "LICENSE_CHECK", "LICENSES_LIST" );
   }

   /** */
   private InternalResult getAvailableLicenses( )
   {
      final List<Integer> availableLicenses = repo.getAvailableLicenses(LICENCES_LIST);
      return InternalResult.ok( U.toMap("licensesList", availableLicenses ) );
   }

   /** */
   private InternalResult checkLicense( InternalRequest request )
   {
      return InternalResult.error( "QUERY_NOT_IMPL", "Query 'LICENSE_CHECK' is not impl yet");
   }

   /** */
   @Override
   public InternalResult handle( InternalRequest request ) {

      if( "LICENSES_LIST".equals( request.queryType() ) )
          return getAvailableLicenses();
      else if( "LICENSE_CHECK".equals(request.queryType()) )
         return checkLicense(request);
      return null;
   }
}
