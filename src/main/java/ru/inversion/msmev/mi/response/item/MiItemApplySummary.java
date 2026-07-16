package ru.inversion.msmev.mi.response.item;

import ru.inversion.utils.IDumpable;

import java.util.List;
import java.util.Map;

public record MiItemApplySummary(
   int totalCount,
   int appliedCount,
   int alreadyAppliedCount,
   int failedCount,
   List<MiItemApplyResult> results
)
   implements IDumpable
{
   @Override
   public void dump( Map<String, Object> properties ) {

   }
}