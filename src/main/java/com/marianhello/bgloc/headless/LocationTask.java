package com.marianhello.bgloc.headless;

import com.marianhello.bgloc.data.BackgroundLocation;

import org.json.JSONException;

public abstract class LocationTask extends Task {
    private BackgroundLocation mLocation;

    public LocationTask(BackgroundLocation location) {
        mLocation = location;
    }

    @Override
    public String getName() {
        return "location";
    }

    @Override
    String getParams() {
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
