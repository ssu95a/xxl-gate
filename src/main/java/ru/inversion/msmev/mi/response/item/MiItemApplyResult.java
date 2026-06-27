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
      FAILED
   }
}