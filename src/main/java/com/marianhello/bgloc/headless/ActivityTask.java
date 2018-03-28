package com.marianhello.bgloc.headless;

import com.marianhello.bgloc.data.BackgroundActivity;

import org.json.JSONException;

public abstract class ActivityTask extends Task {
    private BackgroundActivity mActivity;

    public ActivityTask(BackgroundActivity activity) {
        mActivity = activity;
    }

    @Override
    public String getName() {
        return "activity";
    }

    @Override
    String getParams() {
        if (mActivity == null) {
            return null;
        }

        try {
            return mActivity.toJSONObject().toString();
        } catch (JSONException e) {
            onError("Error processing params: " + e.getMessage());
        }

        return null;
    }
}
