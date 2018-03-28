package com.marianhello.bgloc.headless;

import android.content.Context;
import android.util.Log;

import com.evgenii.jsevaluator.JsEvaluator;

public class HeadlessTaskRunner {
    private JsEvaluator mJsEvaluator;
    private String mJsFunction;

    public static String BUNDLE_KEY = "JS";

    public HeadlessTaskRunner(Context context) {
        mJsEvaluator = new JsEvaluator(context);
    }

    public void setFunction(String jsFunction) {
        mJsFunction = jsFunction;
    }

    public void runTask(Task task) {
        if (mJsFunction == null) {
            task.onError("Cannot run task due missing jsEvaluator. Did you called setFunction?");
            return;
        }

        String jsTask = new StringBuilder()
                .append("function task(name, paramsString) {")
                .append("var params = JSON.parse(paramsString);")
                .append("var task = { name: name, params: params };")
                .append("return(" + mJsFunction + ")(task);")
                .append("}").toString();

        mJsEvaluator.callFunction(jsTask, task, "task", task.getName(), task.getParams());
    }
}
