package ru.inversion.edo.xxl.mi.business.handlers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequest;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequestHandler;
import ru.inversion.edo.xxl.mi.business.MiBusinessResult;

import java.util.Set;

/** <h5>Умершие</h5> */
@Component
@RequiredArgsConstructor
public class Handler_10 implements MiBusinessRequestHandler {

   private final Repository_10 repository;

   @Override
   public Set<Integer> infIds() {
      return Set.of(10);
   }

   @Override
   public MiBusinessResult handle(MiBusinessRequest request ) {
      return repository.apply(request);
   }
}
