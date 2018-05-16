/*
According to apache license

This is fork of christocracy cordova-plugin-background-geolocation plugin
https://github.com/christocracy/cordova-plugin-background-geolocation

This is a new class
*/

package com.marianhello.bgloc;

import android.accounts.Account;
import android.app.Notification;
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
import android.support.v4.content.LocalBroadcastManager;

import com.marianhello.bgloc.data.BackgroundActivity;
import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.bgloc.data.ConfigurationDAO;
import com.marianhello.bgloc.data.DAOFactory;
import com.marianhello.bgloc.data.LocationDAO;
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
import org.json.JSONArray;
import org.json.JSONException;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    public static final int MSG_ON_SERVICE_STOPPED = 104;

    public static final int MSG_ON_SERVICE_STARTED = 105;

    public static final int SERVICE_STOPPED = 0;
    public static final int SERVICE_STARTED = 1;

    private static int sServiceStatus = SERVICE_STOPPED;

    /** notification id */
    private static int NOTIFICATION_ID = 1;

    private ResourceResolver mResolver;
    private LocationDAO mLocationDAO;
    private Config mConfig;
    private LocationProvider mProvider;
    private Account mSyncAccount;
    private boolean mHasConnectivity = true;
    private boolean mIsBound = false;

    private org.slf4j.Logger logger;

    private final IBinder mBinder = new LocalBinder();
    private volatile HandlerThread mHandlerThread;
    private ServiceHandler mServiceHandler;
    private HeadlessTaskRunner mHeadlessTaskRunner;
    private PostLocationTask mPostLocationTask;

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

        stopForeground(true);
        mIsBound = true;
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        logger.debug("Client rebinds to service");

        stopForeground(true);
        mIsBound = true;
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // All clients have unbound with unbindService()
        logger.debug("All clients have been unbound from service");

        Config config = getConfig();
        if (isStarted()) {
            Notification notification = new NotificationHelper.NotificationFactory(this).getNotification(
                    config.getNotificationTitle(),
                    config.getNotificationText(),
                    config.getLargeNotificationIcon(),
                    config.getSmallNotificationIcon(),
                    config.getNotificationIconColor());
            startForeground(NOTIFICATION_ID, notification);
        }
        mIsBound = false;
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
        mHandlerThread = new HandlerThread("LocationService.Thread", Process.THREAD_PRIORITY_BACKGROUND);
        mHandlerThread.start();
        // An Android service handler is a handler running on a specific background thread.
        mServiceHandler = new ServiceHandler(mHandlerThread.getLooper());

        mResolver = ResourceResolver.newInstance(this);
        mLocationDAO = (DAOFactory.createLocationDAO(this));
        mPostLocationTask = new PostLocationTask(mLocationDAO);
        mSyncAccount = AccountHelper.CreateSyncAccount(this, SyncService.ACCOUNT_NAME,
                mResolver.getString(SyncService.ACCOUNT_TYPE_RESOURCE));

        String authority = mResolver.getString(SyncService.AUTHORITY_TYPE_RESOURCE);
        ContentResolver.setIsSyncable(mSyncAccount, authority, 1);
        ContentResolver.setSyncAutomatically(mSyncAccount, authority, true);

        registerReceiver(connectivityChangeReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        NotificationHelper.registerServiceChannel(this);
    }

    @Override
    public void onDestroy() {
        logger.info("Destroying LocationService");
        // workaround for issue #276
        if (mProvider != null) {
            mProvider.onDestroy();
            mProvider = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            mHandlerThread.quitSafely();
        } else {
            mHandlerThread.quit(); //sorry
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

        LocationProviderFactory spf = new LocationProviderFactory(this);
        mProvider = spf.getInstance(mConfig.getLocationProvider());
        mProvider.setDelegate(this);
        mProvider.onCreate();
        mProvider.onConfigure(mConfig);
        mProvider.onStart();

        Bundle bundle = new Bundle();
        bundle.putInt("action", MSG_ON_SERVICE_STARTED);
        broadcastMessage(bundle);

        sServiceStatus = SERVICE_STARTED;

        // We want this service to continue running until it is explicitly stopped
        return START_STICKY;
    }

    public synchronized void start() {
        if (isStarted()) {
            return; // service was already started;
        }

        Intent locationServiceIntent = new Intent(getApplicationContext(), LocationService.class);
//        locationServiceIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        // start service to keep service running even if no clients are bound to it
        startService(locationServiceIntent);

        sServiceStatus = SERVICE_STARTED;
    }

    public synchronized void stop() {
        if (!isStarted()) {
            return;
        }

        if (mProvider != null) {
            mProvider.onStop();
        }

        stopSelf();

        Bundle bundle = new Bundle();
        bundle.putInt("action", MSG_ON_SERVICE_STOPPED);
        broadcastMessage(bundle);

        sServiceStatus = SERVICE_STOPPED;
    }

    public void configure(Config config) {
        if (mConfig == null) {
            mConfig = config;
            return;
        }

        final Config currentConfig = mConfig;
        mConfig = config;

        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
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

        mPostLocationTask.add(location);

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
    }

    public void onStationary(BackgroundLocation location) {
        logger.debug("New stationary {}", location.toString());

        mPostLocationTask.add(location);

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

    public void broadcastMessage(Bundle bundle) {
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
    public void unregisterReceiver (BroadcastReceiver receiver) {
        super.unregisterReceiver(receiver);
    }

    public Config getConfig() {
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

    private void runHeadlessTask(Task task) {
        if (mIsBound) { // only run headless task if there are no bound clients (activity)
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

    /**
     * Location task to post/sync locations from location providers
     *
     * All locations updates are recorded in local db at all times.
     * Also location is also send to all messenger clients.
     *
     * If option.url is defined, each location is also immediately posted.
     * If post is successful, the location is deleted from local db.
     * All failed to post locations are coalesced and send in some time later in one single batch.
     * Batch sync takes place only when number of failed to post locations reaches syncTreshold.
     *
     * If only option.syncUrl is defined, locations are send only in single batch,
     * when number of locations reaches syncTreshold.
     *
     */
    private class PostLocationTask {
        private final ExecutorService mExecutor;
        private final LocationDAO mLocationDAO;

        public PostLocationTask(LocationDAO dao) {
            mLocationDAO = dao;
            mExecutor = Executors.newSingleThreadExecutor();
        }

        public void shutdown() {
            mExecutor.shutdown();
        }

        public void add(final BackgroundLocation location) {
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    long locationId;
                    Config config = getConfig();
                    if (mHasConnectivity && config.hasValidUrl()) {
                        locationId = mLocationDAO.persistLocation(location, config.getMaxLocations());
                        if (postLocation(location)) {
                            mLocationDAO.deleteLocationById(locationId);
                            return;
                        } else {
                            mLocationDAO.updateLocationForSync(locationId);
                        }
                    } else {
                        mLocationDAO.persistLocationForSync(location, config.getMaxLocations());
                    }

                    if (config.hasValidSyncUrl()) {
                        long syncLocationsCount = mLocationDAO.getLocationsForSyncCount(System.currentTimeMillis());
                        if (syncLocationsCount >= config.getSyncThreshold()) {
                            logger.debug("Attempt to sync locations: {} threshold: {}", syncLocationsCount, config.getSyncThreshold());
                            SyncService.sync(mSyncAccount, mResolver.getString(SyncService.AUTHORITY_TYPE_RESOURCE), false);
                        }
                    }
                }
            });
        }

        private boolean postLocation(BackgroundLocation location) {
            logger.debug("Executing PostLocationTask#postLocation");
            JSONArray jsonLocations = new JSONArray();
            Config config = getConfig();
            try {
                jsonLocations.put(config.getTemplate().locationToJson(location));
            } catch (JSONException e) {
                logger.warn("Location to json failed: {}", location.toString());
                return false;
            }

            String url = config.getUrl();
            logger.debug("Posting json to url: {} headers: {}", url, config.getHttpHeaders());
            int responseCode;

            try {
                responseCode = HttpPostService.postJSON(url, jsonLocations, config.getHttpHeaders());
            } catch (Exception e) {
                mHasConnectivity = isNetworkAvailable();
                logger.warn("Error while posting locations: {}", e.getMessage());
                return false;
            }

            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_CREATED) {
                logger.warn("Server error while posting locations responseCode: {}", responseCode);
                return false;
            }

            return true;
        }
    }

    /**
     * Broadcast receiver which detects connectivity change condition
     */
    private BroadcastReceiver connectivityChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mHasConnectivity = isNetworkAvailable();
            logger.info("Network condition changed has connectivity: {}", mHasConnectivity);
        }
    };

    private boolean isNetworkAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    public static boolean isStarted() {
        return sServiceStatus == SERVICE_STARTED;
    }
}
