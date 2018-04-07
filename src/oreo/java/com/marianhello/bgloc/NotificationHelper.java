package com.marianhello.bgloc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.support.v4.app.NotificationCompat;

import com.marianhello.logging.LoggerManager;

public class NotificationHelper {
    public static final String SERVICE_CHANNEL_ID = "bglocservice";

    public static final String SYNC_CHANNEL_ID = "syncservice";
    public static final String SYNC_CHANNEL_NAME = "Sync Service";
    public static final String SYNC_CHANNEL_DESCRIPTION = "Shows sync progress";

    public static class NotificationFactory {
        private Context mContext;
        private ResourceResolver mResolver;

        private org.slf4j.Logger logger;

        public NotificationFactory(Context context) {
            mContext = context;
            mResolver = ResourceResolver.newInstance(context);
            logger =  LoggerManager.getLogger(NotificationFactory.class);
        }

        private Integer parseNotificationIconColor(String color) {
            int iconColor = 0;
            if (color != null) {
                try {
                    iconColor = Color.parseColor(color);
                } catch (IllegalArgumentException e) {
                    logger.error("Couldn't parse color from android options");
                }
            }
            return iconColor;
        }

        public Notification getNotification(String title, String text, String largeIcon, String smallIcon, String color) {
            Context appContext = mContext.getApplicationContext();

            // Build a Notification required for running service in foreground.
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, NotificationHelper.SERVICE_CHANNEL_ID);

            builder.setContentTitle(title);
            builder.setContentText(text);
            if (smallIcon != null && !smallIcon.isEmpty()) {
                builder.setSmallIcon(mResolver.getDrawable(smallIcon));
            } else {
                builder.setSmallIcon(android.R.drawable.ic_menu_mylocation);
            }
            if (largeIcon != null && !largeIcon.isEmpty()) {
                builder.setLargeIcon(BitmapFactory.decodeResource(appContext.getResources(), mResolver.getDrawable(largeIcon)));
            }
            if (color != null && !color.isEmpty()) {
                builder.setColor(this.parseNotificationIconColor(color));
            }

            // Add an onclick handler to the notification
            String packageName = appContext.getPackageName();
            Intent launchIntent = appContext.getPackageManager().getLaunchIntentForPackage(packageName);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(appContext, 0, launchIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            builder.setContentIntent(contentIntent);

            Notification notification = builder.build();
            notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE | Notification.FLAG_NO_CLEAR;

            return notification;
        }
    }

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
