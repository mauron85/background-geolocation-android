package com.marianhello.bgloc.headless;

import android.content.Context;

public class TaskRunnerFactory {
    private Context mContext;

    public TaskRunnerFactory(Context context) {
        mContext = context;

    }

    public TaskRunner getTaskRunner(String className) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return (TaskRunner) Class.forName(className).newInstance();
    }
}
