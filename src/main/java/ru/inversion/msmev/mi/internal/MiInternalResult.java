package ru.inversion.msmev.mi.internal;

import java.util.Map;

public record MiInternalResult(
   boolean success,
   String resultCode,
   String resultInfo,
   Map<String, Object> parameters
) { }