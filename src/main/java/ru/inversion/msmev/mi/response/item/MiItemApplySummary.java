package ru.inversion.msmev.mi.response.item;

import java.util.List;

public record MiItemApplySummary(
        int totalCount,
        int appliedCount,
        int alreadyAppliedCount,
        int failedCount,
        List<MiItemApplyResult> results
) {
   public boolean partial()
   {
      return failedCount > 0;
   }
}