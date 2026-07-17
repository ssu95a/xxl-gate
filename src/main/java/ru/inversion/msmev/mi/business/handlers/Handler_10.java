package ru.inversion.msmev.mi.business.handlers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.msmev.mi.business.MiBusinessRequest;
import ru.inversion.msmev.mi.business.MiBusinessRequestHandler;
import ru.inversion.msmev.mi.business.MiBusinessResponse;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class Handler_10 implements MiBusinessRequestHandler {

   private final Repository_10 repository;

   @Override
   public Set<Integer> infIds() {
      return Set.of(10);
   }

   @Override
   public MiBusinessResponse handle( MiBusinessRequest request ) {
      return repository.apply(request);
   }
}
