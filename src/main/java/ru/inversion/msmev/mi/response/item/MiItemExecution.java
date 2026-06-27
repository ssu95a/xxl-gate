package ru.inversion.msmev.mi.response.item;

import java.util.UUID;

record MiItemExecution(
        int itemIndex,
        UUID itemExternalUuid,
        MiItemApplyResult result,
        Throwable failure
) {
}