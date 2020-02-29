package com.marianhello.bgloc.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface LocationTransform
{
    /**
     * Return a <code>BackgroundLocation</code>, either a new one or the same one after modification.
     * Return <code>null</code> to prevent this location from being committed.
     * @param context
     * @param location - the input location
     * @return the location that you want to actually commit
     */

    @Nullable
    BackgroundLocation transformLocationBeforeCommit(@NonNull Context context, @NonNull BackgroundLocation location);
}