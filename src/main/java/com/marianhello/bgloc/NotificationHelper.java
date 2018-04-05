package com.marianhello.bgloc;

import android.app.NotificationChannel;
import android.content.Context;
import android.os.Build;

public class NotificationHelper {
    public static final String SERVICE_CHANNEL_ID = "bglocservice";

    public static final String SYNC_CHANNEL_ID = "syncservice";
    public static final String SYNC_CHANNEL_NAME = "Sync Service";
    public static final String SYNC_CHANNEL_DESCRIPTION = "Shows sync progress";

    public static void registerAllChannels(Context context) {
        registerServiceChannel(context);
        registerSyncChannel(context);
    }

    public static void registerServiceChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String appName = ResourceResolver.newInstance(context).getString(("app_name"));
            android.app.NotificationManager notificationManager = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel serviceChannel = new NotificationChannel(SERVICE_CHANNEL_ID, appName, android.app.NotificationManager.IMPORTANCE_DEFAULT);
            // Register the channel with the system
            notificationManager.createNotificationChannel(serviceChannel);
        }
    }

    public static void registerSyncChannel(Context context ){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationManager notificationManager = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            NotificationChannel syncChannel = new NotificationChannel(SYNC_CHANNEL_ID, SYNC_CHANNEL_NAME, android.app.NotificationManager.IMPORTANCE_DEFAULT);
            syncChannel.setDescription(SYNC_CHANNEL_DESCRIPTION);
            // Register the channel with the system
            notificationManager.createNotificationChannel(syncChannel);
        }
    }
}
