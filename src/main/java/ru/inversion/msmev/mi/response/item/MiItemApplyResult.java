package ru.inversion.msmev.mi.response.item;

import java.util.UUID;

public record MiItemApplyResult(
   int itemIndex,
   UUID itemExternalUuid,
   Status status,
   String resultCode,
   String resultInfo
)
{
   public enum Status {
      APPLIED,
      ALREADY_APPLIED,
      FAILED;

      public static Status ofInt( int v ) {
         return switch (v) {
            case 0 -> APPLIED;
            case 1,2 -> ALREADY_APPLIED;
            default -> FAILED;
         };
      }

   }
}