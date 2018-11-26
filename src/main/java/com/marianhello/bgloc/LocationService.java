/*
According to apache license

This is fork of christocracy cordova-plugin-background-geolocation plugin
https://github.com/christocracy/cordova-plugin-background-geolocation

This is a new class
*/

package com.marianhello.bgloc;

import android.accounts.Account;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import com.marianhello.bgloc.data.BackgroundActivity;
import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.bgloc.data.ConfigurationDAO;
import com.marianhello.bgloc.data.DAOFactory;
import com.marianhello.bgloc.headless.ActivityTask;
import com.marianhello.bgloc.headless.HeadlessTaskRunner;
import com.marianhello.bgloc.headless.LocationTask;
import com.marianhello.bgloc.headless.StationaryTask;
import com.marianhello.bgloc.headless.Task;
import com.marianhello.bgloc.provider.LocationProvider;
import com.marianhello.bgloc.provider.LocationProviderFactory;
import com.marianhello.bgloc.provider.ProviderDelegate;
import com.marianhello.bgloc.sync.AccountHelper;
import com.marianhello.bgloc.sync.SyncService;
import com.marianhello.logging.LoggerManager;
import com.marianhello.logging.UncaughtExceptionLogger;

import org.chromium.content.browser.ThreadUtils;
import org.json.JSONException;

public class LocationService extends Service implements ProviderDelegate {

    public static final String ACTION_BROADCAST = ".broadcast";

    /**
     * Command sent by the service to
     * any registered clients with error.
     */
    public static final int MSG_ON_ERROR = 100;

    /**
     * Command sent by the service to
     * any registered clients with the new position.
     */
    public static final int MSG_ON_LOCATION = 101;

    /**
     * Command sent by the service to
     * any registered clients whenever the devices enters "stationary-mode"
     */
    public static final int MSG_ON_STATIONARY = 102;

    /**
     * Command sent by the service to
     * any registered clients with new detected activity.
     */
    public static final int MSG_ON_ACTIVITY = 103;

    public static final int MSG_ON_SERVICE_STARTED = 104;

    public static final int MSG_ON_SERVICE_STOPPED = 105;

    public static final int MSG_ON_ABORT_REQUESTED = 106;

    public static final int MSG_ON_HTTP_AUTHORIZATION = 107;

    /** notification id */
    private static int NOTIFICATION_ID = 1;

    private ResourceResolver mResolver;
    private Config mConfig;
    private LocationProvider mProvider;
    private Account mSyncAccount;

    private org.slf4j.Logger logger;

    private final IBinder mBinder = new LocalBinder();
    private HandlerThread mHandlerThread;
    private ServiceHandler mServiceHandler;
    private PostLocationTask mPostLocationTask;
    private HeadlessTaskRunner mHeadlessTaskRunner;
    private LocationProviderFactory mLocationProviderFactory;

    private static ILocationTransform sLocationTransform;

    private class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        logger.debug("Client binds to service");

        if (!getConfig().getStartForeground()) {
            stopForeground(true);
        }

        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        logger.debug("Client rebinds to service");

        if (!getConfig().getStartForeground()) {
            stopForeground(true);
        }

        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // All clients have unbound with unbindService()
        logger.debug("All clients have been unbound from service");

        if (isStarted() && !isInForeground()) {
            startForeground();
        }

        return true; // Ensures onRebind() is called when a client re-binds.
    }

    @Override
    public void onCreate() {
        super.onCreate();

        UncaughtExceptionLogger.register(this);

        logger = LoggerManager.getLogger(LocationService.class);
        logger.info("Creating LocationService");

        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        if (mHandlerThread == null) {
            mHandlerThread = new HandlerThread("LocationService.Thread", Process.THREAD_PRIORITY_BACKGROUND);
        }
        mHandlerThread.start();
        // An Android service handler is a handler running on a specific background thread.
        mServiceHandler = new ServiceHandler(mHandlerThread.getLooper());

        mResolver = ResourceResolver.newInstance(this);

        mSyncAccount = AccountHelper.CreateSyncAccount(this, mResolver.getAccountName(),
                mResolver.getAccountType());

        String authority = mResolver.getAuthority();
        ContentResolver.setIsSyncable(mSyncAccount, authority, 1);
        ContentResolver.setSyncAutomatically(mSyncAccount, authority, true);

        mPostLocationTask = new PostLocationTask((DAOFactory.createLocationDAO(this)),
                new PostLocationTask.PostLocationTaskListener() {
            @Override
            public void onRequestedAbortUpdates() {
                handleRequestedAbortUpdates();
            }

            @Override
            public void onHttpAuthorizationUpdates() {
                handleHttpAuthorizationUpdates();
            }

            @Override
            public void onSyncRequested() {
                SyncService.sync(mSyncAccount, mResolver.getAuthority(), false);
            }
        }, new ConnectivityListener() {
            @Override
            public boolean hasConnectivity() {
                return isNetworkAvailable();
            }
        });

        registerReceiver(connectivityChangeReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        NotificationHelper.registerServiceChannel(this);
    }

    @Override
    public void onDestroy() {
        logger.info("Destroying LocationService");

        // workaround for issue #276
        if (mProvider != null) {
            mProvider.onDestroy();
        }

        if (mHandlerThread != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mHandlerThread.quitSafely();
            } else {
                mHandlerThread.quit(); //sorry
            }
        }

        if (mPostLocationTask != null) {
            mPostLocationTask.shutdown();
        }


        unregisterReceiver(connectivityChangeReceiver);

        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        logger.debug("Task has been removed");
        // workaround for issue #276
        Config config = getConfig();
        if (config.getStopOnTerminate()) {
            logger.info("Stopping self");
            stopSelf();
        } else {
            logger.info("Continue running in background");
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logger.info("Service started");

        if (mConfig == null) {
            logger.warn("Attempt to start unconfigured service. Will use stored or default.");
            mConfig = getConfig();
            // TODO: throw JSONException if config cannot be obtained from db
        }

        logger.debug("Will start service with: {}", mConfig.toString());

        mPostLocationTask.setConfig(mConfig);
        mPostLocationTask.clearQueue();

        LocationProviderFactory spf = mLocationProviderFactory != null
                ? mLocationProviderFactory : new LocationProviderFactory(this);
        mProvider = spf.getInstance(mConfig.getLocationProvider());
        mProvider.setDelegate(this);
        mProvider.onCreate();
        mProvider.onConfigure(mConfig);
        mProvider.onStart();

        if (mConfig.getStartForeground()) {
            startForeground();
        }

        broadcastMessage(MSG_ON_SERVICE_STARTED);

        // We want this service to continue running until it is explicitly stopped
        return START_STICKY;
    }

    private void startForeground() {
        Config config = getConfig();
        Notification notification = new NotificationHelper.NotificationFactory(this).getNotification(
                config.getNotificationTitle(),
                config.getNotificationText(),
                config.getLargeNotificationIcon(),
                config.getSmallNotificationIcon(),
                config.getNotificationIconColor());
        super.startForeground(NOTIFICATION_ID, notification);
    }

    public synchronized void start() {
        if (isStarted()) {
            return; // service was already started;
        }

        Intent locationServiceIntent = new Intent(getApplicationContext(), LocationService.class);
//        locationServiceIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        // start service to keep service running even if no clients are bound to it
        startService(locationServiceIntent);
    }

    public synchronized void stop() {
        if (!isStarted()) {
            return;
        }

        if (mProvider != null) {
            mProvider.onStop();
        }

        if (isInForeground()) {
            stopForeground(true);
        }
        stopSelf();

        broadcastMessage(MSG_ON_SERVICE_STOPPED);
    }

    public void configure(Config config) {
        if (mConfig == null) {
            mConfig = config;
            return;
        }

        final Config currentConfig = mConfig;
        mConfig = config;

        mPostLocationTask.setConfig(mConfig);

        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isStarted()) {
                    if (currentConfig.getStartForeground() == true && mConfig.getStartForeground() == false) {
                        stopForeground(true);
                    }

                    if (mConfig.getStartForeground() == true) {
                        if (currentConfig.getStartForeground() == false) {
                            // was not running in foreground, so start in foreground
                            startForeground();
                        } else {
                            // was running in foreground, so just update existing notification
                            Notification notification = new NotificationHelper.NotificationFactory(LocationService.this).getNotification(
                                    mConfig.getNotificationTitle(),
                                    mConfig.getNotificationText(),
                                    mConfig.getLargeNotificationIcon(),
                                    mConfig.getSmallNotificationIcon(),
                                    mConfig.getNotificationIconColor());

                            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                            notificationManager.notify(NOTIFICATION_ID, notification);
                        }
                    }
                }

                if (currentConfig.getLocationProvider() != mConfig.getLocationProvider()) {
                    boolean shouldStart = mProvider.isStarted();
                    mProvider.onDestroy();
                    LocationProviderFactory spf = new LocationProviderFactory(LocationService.this);
                    mProvider = spf.getInstance(mConfig.getLocationProvider());
                    mProvider.setDelegate(LocationService.this);
                    mProvider.onCreate();
                    mProvider.onConfigure(mConfig);
                    if (shouldStart) {
                        mProvider.onStart();
                    }
                } else {
                    mProvider.onConfigure(mConfig);
                }
            }
        });
    }

    public void registerHeadlessTask(String jsFunction) {
        logger.debug("Registering headless task");
        mHeadlessTaskRunner = new HeadlessTaskRunner(this);
        mHeadlessTaskRunner.setFunction(jsFunction);
    }

    public void onLocation(BackgroundLocation location) {
        logger.debug("New location {}", location.toString());

        Bundle bundle = new Bundle();
        bundle.putInt("action", MSG_ON_LOCATION);
        bundle.putParcelable("payload", location);
        broadcastMessage(bundle);

        runHeadlessTask(new LocationTask(location) {
            @Override
            public void onError(String errorMessage) {
                logger.error("Location task error: {}", errorMessage);
            }

            @Override
            public void onResult(String value) {
                logger.debug("Location task result: {}", value);
            }
        });

        handleLocation(location);
    }

    public void onStationary(BackgroundLocation location) {
        logger.debug("New stationary {}", location.toString());

        Bundle bundle = new Bundle();
        bundle.putInt("action", MSG_ON_STATIONARY);
        bundle.putParcelable("payload", location);
        broadcastMessage(bundle);

        runHeadlessTask(new StationaryTask(location){
            @Override
            public void onError(String errorMessage) {
                logger.error("Stationary task error: {}", errorMessage);
            }

            @Override
            public void onResult(String value) {
                logger.debug("Stationary task result: {}", value);
            }
        });

        handleLocation(location);
    }

    public void onActivity(BackgroundActivity activity) {
        logger.debug("New activity {}", activity.toString());

        Bundle bundle = new Bundle();
        bundle.putInt("action", MSG_ON_ACTIVITY);
        bundle.putParcelable("payload", activity);
        broadcastMessage(bundle);

        runHeadlessTask(new ActivityTask(activity){
            @Override
            public void onError(String errorMessage) {
                logger.error("Activity task error: {}", errorMessage);
            }

            @Override
            public void onResult(String value) {
                logger.debug("Activity task result: {}", value);
            }
        });
    }

    public void onError(PluginException error) {
        Bundle bundle = new Bundle();
        bundle.putInt("action", MSG_ON_ERROR);
        bundle.putBundle("payload", error.toBundle());
        broadcastMessage(bundle);
    }

    private void broadcastMessage(int msgId) {
        Bundle bundle = new Bundle();
        bundle.putInt("action", msgId);
        broadcastMessage(bundle);
    }

    private void broadcastMessage(Bundle bundle) {
        Intent intent = new Intent(ACTION_BROADCAST);
        intent.putExtras(bundle);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    public void executeProviderCommand(final int command, final int arg1) {
        if (mProvider == null) {
            return;
        }

        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProvider.onCommand(command, arg1);
            }
        });
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        return super.registerReceiver(receiver, filter, null, mServiceHandler);
    }

    @Override
    public void unregisterReceiver(BroadcastReceiver receiver) {
        try {
            super.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ex) {
            // if was not registered ignore exception
        }
    }

    Config getConfig() {
        Config config = mConfig;
        if (config == null) {
            ConfigurationDAO dao = DAOFactory.createConfigurationDAO(this);
            try {
                config = dao.retrieveConfiguration();
            } catch (JSONException e) {
                logger.error("Config exception: {}", e.getMessage());
            }
        }

        if (config == null) {
            config = Config.getDefault();
        }

        mConfig = config;
        return mConfig;
    }

    void setHandlerThread(HandlerThread handlerThread) {
        mHandlerThread = handlerThread;
    }

    void setLocationProviderFactory(LocationProviderFactory factory) {
        mLocationProviderFactory = factory;
    }

    private void runHeadlessTask(Task task) {
        if (isBound()) { // only run headless task if there are no bound clients (activity)
            return;
        }

        logger.debug("Running headless task: {}", task);
        if (mHeadlessTaskRunner == null) {
            logger.warn("HeadlessTaskRunner is null. Skipping task: {}", task);
            return;
        }

        mHeadlessTaskRunner.runTask(task);
    }

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        LocationService getService() {
            return LocationService.this;
        }
    }

    private void handleLocation(BackgroundLocation location) {
        if (sLocationTransform != null) {
            location = sLocationTransform.transformLocationBeforeCommit(LocationService.this, location);

            if (location == null) {
                logger.debug("Skipping coordinate as requested by the locationTransform");
                return;
            }
        }

        if (mPostLocationTask != null) {
            mPostLocationTask.add(location);
        }
    }

    public void handleRequestedAbortUpdates() {
        broadcastMessage(MSG_ON_ABORT_REQUESTED);
    }

    public void handleHttpAuthorizationUpdates() {
        broadcastMessage(MSG_ON_HTTP_AUTHORIZATION);
    }

    /**
     * Broadcast receiver which detects connectivity change condition
     */
    private BroadcastReceiver connectivityChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean hasConnectivity = isNetworkAvailable();
            mPostLocationTask.setHasConnectivity(hasConnectivity);
            logger.info("Network condition changed has connectivity: {}", hasConnectivity);
        }
    };

    private boolean isNetworkAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private ActivityManager.RunningServiceInfo getServiceInfo() {
        String serviceName = LocationService.class.getName();
        ActivityManager manager = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceName.equals(service.service.getClassName())) {
                return service;
            }
        }
        return null;
    }

    public boolean isBound() {
        ActivityManager.RunningServiceInfo serviceInfo = getServiceInfo();
        if (serviceInfo != null) {
            return serviceInfo.clientCount > 0;
        }

        return false;
    }

    public boolean isInForeground() {
        ActivityManager.RunningServiceInfo serviceInfo = getServiceInfo();
        if (serviceInfo != null) {
            return serviceInfo.foreground;
        }

        return false;
    }

    public boolean isStarted() {
        ActivityManager.RunningServiceInfo serviceInfo = getServiceInfo();
        if (serviceInfo != null) {
            return serviceInfo.started;
        }

        return false;
    }

    public static void setLocationTransform(@Nullable ILocationTransform transform) {
        sLocationTransform = transform;
    }

    public static @Nullable ILocationTransform getLocationTransform() {
        return sLocationTransform;
    }

    public interface ILocationTransform
    {
        /**
         * Return a <code>BackgroundLocation</code>, either a new one or the same one after modification.
         * Return <code>null</code> to prevent this location from being committed.
         * @param context
         * @param location - the input location
         * @return the location that you want to actually commit
         */

        @Nullable BackgroundLocation transformLocationBeforeCommit(@NonNull Context context, @NonNull BackgroundLocation location);
    }
}
