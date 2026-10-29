package ru.inversion.edo.xxl.mi.business.handlers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequest;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequestHandler;
import ru.inversion.edo.xxl.mi.business.MiBusinessResult;

import java.util.Set;

/** <h5>Доходы физ лиц </h5> */
@Component
@RequiredArgsConstructor
public class Handler_81 implements MiBusinessRequestHandler {

   private final Repository_81 repository;

   @Override
   public Set<Integer> infIds() {
      return Set.of(81);
   }

   @Override
   public MiBusinessResult handle(MiBusinessRequest request ) {
      return repository.apply(request);
   }
}
