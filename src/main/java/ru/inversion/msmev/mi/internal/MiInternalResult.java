package ru.inversion.msmev.mi.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record MiInternalResult(
        boolean success,
        String resultCode,
        String resultInfo,
        Map<String, Object> data
)
{
   public MiInternalResult
   {
      data =
              data == null || data.isEmpty()
                      ? Map.of()
                      : Collections.unmodifiableMap(
                      new LinkedHashMap<>(data)
              );
   }
}