package com.marianhello.bgloc.headless;

import android.os.Bundle;

import com.marianhello.bgloc.data.BackgroundLocation;

import org.json.JSONException;

public abstract class StationaryTask extends Task {
    private BackgroundLocation mLocation;

    public StationaryTask(BackgroundLocation location) {
        mLocation = location;
    }

    @Override
    public String getName() {
        return "stationary";
    }

    @Override
    public Bundle getBundle() {
        return null;
    }

    @Override
    public String toString() {
        if (mLocation == null) {
            return null;
        }

        try {
            return mLocation.toJSONObject().toString();
        } catch (JSONException e) {
            onError("Error processing params: " + e.getMessage());
        }

        return null;
    }
}
