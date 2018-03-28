package com.marianhello.bgloc.headless;

import com.evgenii.jsevaluator.interfaces.JsCallback;

public abstract class Task implements JsCallback {
    abstract String getName();
    abstract String getParams();
}
