package com.marianhello.bgloc.service;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;

import com.marianhello.bgloc.Config;

public class LocationServiceProxy implements LocationService, LocationServiceInfo {
    private final Context mContext;
    private final LocationServiceIntentBuilder mIntentBuilder;

    public LocationServiceProxy(Context context) {
        mContext = context;
        mIntentBuilder = new LocationServiceIntentBuilder(context);
    }

    @Override
    public void configure(Config config) {
        // do not start service if it was not already started
        // FIXES:
        // https://github.com/mauron85/react-native-background-geolocation/issues/360
        // https://github.com/mauron85/cordova-plugin-background-geolocation/issues/551
        // https://github.com/mauron85/cordova-plugin-background-geolocation/issues/552
        if (!isRunning()) { return; }

        Intent intent = mIntentBuilder
                .setCommand(CommandId.CONFIGURE, config)
                .build();
        executeIntentCommand(intent);
    }

    @Override
    public void registerHeadlessTask(String jsFunction) {
        Intent intent = mIntentBuilder
                .setCommand(CommandId.REGISTER_HEADLESS_TASK, jsFunction)
                .build();
        executeIntentCommand(intent);
    }

    @Override
    public void startHeadlessTask() {
        Intent intent = mIntentBuilder
                .setCommand(CommandId.START_HEADLESS_TASK)
                .build();
        executeIntentCommand(intent);
    }

    @Override
    public void executeProviderCommand(int command, int arg) {
        // TODO
    }

    @Override
    public void start() {
        Intent intent = mIntentBuilder.setCommand(CommandId.START).build();
//        intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        // start service to keep service running even if no clients are bound to it
        executeIntentCommand(intent);
    }

    @Override
    public void stop() {
        Intent intent = mIntentBuilder.setCommand(CommandId.STOP).build();
        executeIntentCommand(intent);
    }

    @Override
    public void stopForeground() {
        if (!isRunning()) { return; }

        Intent intent = mIntentBuilder.setCommand(CommandId.STOP_FOREGROUND).build();
        executeIntentCommand(intent);
    }

    @Override
    public void startForeground() {
        if (!isRunning()) { return; }

        Intent intent = mIntentBuilder.setCommand(CommandId.START_FOREGROUND).build();
        executeIntentCommand(intent);
    }

    @Override
    public boolean isStarted() {
        LocationServiceInfo serviceInfo = new LocationServiceInfoImpl(mContext);
        return serviceInfo.isStarted();
    }

    public boolean isRunning() {
        if (isStarted()) {
            return LocationServiceImpl.isRunning();
        }
        return false;
    }

    @Override
    public boolean isBound() {
        LocationServiceInfo serviceInfo = new LocationServiceInfoImpl(mContext);
        return serviceInfo.isBound();
    }

    private void executeIntentCommand(Intent intent) {
        mContext.startService(intent);
    }
}
