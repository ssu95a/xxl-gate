package ru.inversion.msmev.mi.internal;

import java.util.Map;

/** */
public record MiInternalQuery (
   String queryType,
   Map<String, Object> params
)
{
   public MiInternalQuery
   {
      params = params == null ? Map.of() : Map.copyOf(params);
   }
}