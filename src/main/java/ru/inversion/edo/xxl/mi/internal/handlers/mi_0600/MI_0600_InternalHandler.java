package ru.inversion.edo.xxl.mi.internal.handlers.mi_0600;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.mi.internal.InternalRequest;
import ru.inversion.edo.xxl.mi.internal.InternalRequestHandler;
import ru.inversion.edo.xxl.mi.internal.InternalResult;
import ru.inversion.utils.U;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class MI_0600_InternalHandler implements InternalRequestHandler {

   final private MI_0600_InternalRepository repo;

   @Override
   public Set<String> queryTypes() {
      return Set.of("mi_0600_get_schedule");
   }

   /** */
   @Override
   public InternalResult handle(InternalRequest request) {
      return InternalResult.ok( U.toMap("licensesList", repo.getSchedule() ) );
   }
}
