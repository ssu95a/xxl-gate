package ru.inversion.msmev.mi.business.handlers;

import ru.inversion.msmev.mi.business.MiBusinessRequest;
import ru.inversion.msmev.mi.business.MiBusinessRequestHandler;
import ru.inversion.msmev.mi.business.MiBusinessResponse;

import java.util.Set;

public class Handler_23 implements MiBusinessRequestHandler {

   @Override
   public Set<Integer> infIds() {
      return Set.of(23);
   }

   @Override
   public MiBusinessResponse handle( MiBusinessRequest request ) {
      return null;
   }
}
