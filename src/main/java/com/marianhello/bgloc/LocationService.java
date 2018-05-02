/*
According to apache license

This is fork of christocracy cordova-plugin-background-geolocation plugin
https://github.com/christocracy/cordova-plugin-background-geolocation

This is a new class
*/

package com.marianhello.bgloc;

import android.accounts.Account;
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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;

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

import org.json.JSONArray;
import org.json.JSONException;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationService extends Service implements ProviderDelegate {

    /** Keeps track of all current registered clients. */
    HashMap<Integer, Messenger> mClients = new HashMap();

    /**
     * Command sent by the service to
     * any registered clients with error.
     */
    public static final int MSG_ERROR = 1;

    /**
     * Command to the service to register a client, receiving callbacks
     * from the service.  The Message's replyTo field must be a Messenger of
     * the client where callbacks should be sent.
     */
    public static final int MSG_REGISTER_CLIENT = 2;

    /**
     * Command to the service to unregister a client, to stop receiving callbacks
     * from the service.  The Message's replyTo field must be a Messenger of
     * the client as previously given with MSG_REGISTER_CLIENT.
     */
    public static final int MSG_UNREGISTER_CLIENT = 3;

    /**
     * Command sent by the service to
     * any registered clients with the new position.
     */
    public static final int MSG_LOCATION_UPDATE = 4;

    /**
     * Command sent by the service to
     * any registered clients whenever the devices enters "stationary-mode"
     */
    public static final int MSG_ON_STATIONARY = 5;

    /**
     * Command to the active location provider
     */
    public static final int MSG_EXEC_COMMAND = 6;

    /**
     * @Deprecated use MSG_EXEC_COMMAND instead
     * Command to the service to indicate operation mode has been changed
     */
    public static final int MSG_SWITCH_MODE = MSG_EXEC_COMMAND;

    /**
     * Command to the service to indicate configuration has been changed
     */
    public static final int MSG_CONFIGURE = 7;

    /**
     * Command sent by the service to
     * any registered clients with new detected activity.
     */
    public static final int MSG_ON_ACTIVITY = 8;

    /**
     * Command to the service to register headless task
     */
    public static final int MSG_REGISTER_HEADLESS_TASK = 9;

    /** indicate if service is running */
    private static Boolean isRunning = false;

    /** notification id */
    private static int NOTIF_ID = 1;

    private static final int ONE_MINUTE_IN_MILLIS = 1000 * 60;

    private ResourceResolver mResolver;
    private LocationDAO mLocationDAO;
    private Config mConfig;
    private LocationProvider mProvider;
    private Account mSyncAccount;
    private boolean mHasConnectivity = true;
    private boolean mHasBoundClients = false;

    private org.slf4j.Logger logger;

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
        }
    }

    /**
     * Handler of incoming messages from clients.
     */
    private class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            logger.debug("Handler received message: {}", msg);
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    mClients.put(msg.arg1, msg.replyTo);
                    break;
                case MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.arg1);
                    break;
                case MSG_EXEC_COMMAND:
                    sendCommand(msg.arg1);
                    break;
                case MSG_CONFIGURE:
                    configure(msg.getData());
                    break;
                case MSG_REGISTER_HEADLESS_TASK:
                    registerHeadlessTask(msg.getData());
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger messenger = new Messenger(new IncomingHandler());

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        logger.debug("Client bind to service");
        mHasBoundClients = true;
        return messenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // All clients have unbound with unbindService()
        logger.debug("All clients have unbound from service");
        mHasBoundClients = false;
        return false;
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
        mHandlerThread = new HandlerThread("LocationService.HandlerThread", Process.THREAD_PRIORITY_BACKGROUND);
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

        isRunning = false;
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
        logger.info("Received start command startId: {} intent: {}", startId, intent);

        if (mProvider != null) {
            mProvider.onDestroy();
        }

        if (intent == null) {
            //service has been probably restarted so we need to load config from db
            mConfig = getConfig();
        } else {
            if (intent.hasExtra("config")) {
                mConfig = intent.getParcelableExtra("config");
            } else {
                mConfig = Config.getDefault(); //using default config
            }
        }

        logger.debug("Will start service with: {}", mConfig.toString());

        LocationProviderFactory spf = new LocationProviderFactory(this);
        mProvider = spf.getInstance(mConfig.getLocationProvider());
        mProvider.setDelegate(this);
        mProvider.onCreate();
        mProvider.onConfigure(mConfig);
        mProvider.onStart();

        if (mConfig.getStartForeground()) {
            Notification notification = new NotificationHelper.NotificationFactory(this).getNotification(
                    mConfig.getNotificationTitle(),
                    mConfig.getNotificationText(),
                    mConfig.getLargeNotificationIcon(),
                    mConfig.getSmallNotificationIcon(),
                    mConfig.getNotificationIconColor());
            startForeground(NOTIF_ID, notification);
        }

        isRunning = true;

        //We want this service to continue running until it is explicitly stopped
        return START_STICKY;
    }

    private void sendCommand(int mode) {
        mProvider.onCommand(mode);
    }

    private void configure(Config config) {
        if (!isRunning()) {
            return; // do not configure stopped service it will be configured when started
        }

        Config currentConfig = mConfig;
        mConfig = config;

        if (currentConfig.getStartForeground() == true && mConfig.getStartForeground() == false) {
            stopForeground(true);
        }

        if (mConfig.getStartForeground() == true) {
            Notification notification = new NotificationHelper.NotificationFactory(this).getNotification(
                    mConfig.getNotificationTitle(),
                    mConfig.getNotificationText(),
                    mConfig.getLargeNotificationIcon(),
                    mConfig.getSmallNotificationIcon(),
                    mConfig.getNotificationIconColor());

            if (currentConfig.getStartForeground() == false) {
                startForeground(NOTIF_ID, notification);
            } else {
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(NOTIF_ID, notification);
            }
        }

        if (currentConfig.getLocationProvider() != mConfig.getLocationProvider()) {
            boolean shouldStart = mProvider.isStarted();
            mProvider.onDestroy();
            LocationProviderFactory spf = new LocationProviderFactory(this);
            mProvider = spf.getInstance(mConfig.getLocationProvider());
            mProvider.setDelegate(this);
            mProvider.onCreate();
            mProvider.onConfigure(mConfig);
            if (shouldStart) {
                mProvider.onStart();
            }
        } else {
            mProvider.onConfigure(mConfig);
        }
    }

    private void configure(Bundle bundle) {
        Config config = bundle.getParcelable(Config.BUNDLE_KEY);
        configure(config);
    }

    private void registerHeadlessTask(Bundle bundle) {
        logger.debug("Registering headless task");
        String jsFunction = bundle.getString(HeadlessTaskRunner.BUNDLE_KEY);
        mHeadlessTaskRunner = new HeadlessTaskRunner(this);
        mHeadlessTaskRunner.setFunction(jsFunction);
    }

    public void onLocation(BackgroundLocation location) {
        logger.debug("New location {}", location.toString());

        mPostLocationTask.add(location);

        Bundle bundle = new Bundle();
        bundle.putParcelable(BackgroundLocation.BUNDLE_KEY, location);
        Message msg = Message.obtain(null, MSG_LOCATION_UPDATE);
        msg.setData(bundle);

        sendClientMessage(msg);
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
        bundle.putParcelable(BackgroundLocation.BUNDLE_KEY, location);
        Message msg = Message.obtain(null, MSG_ON_STATIONARY);
        msg.setData(bundle);

        sendClientMessage(msg);
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
        bundle.putParcelable(BackgroundActivity.BUNDLE_KEY, activity);
        Message msg = Message.obtain(null, MSG_ON_ACTIVITY);
        msg.setData(bundle);

        sendClientMessage(msg);
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

    public void sendClientMessage(Message msg) {
        Iterator<Messenger> it = mClients.values().iterator();
        while (it.hasNext()) {
            try {
                Messenger client = it.next();
                client.send(msg);
            } catch (RemoteException e) {
                // The client is dead.  Remove it from the list;
                // we are going through the list from back to front
                // so this is safe to do inside the loop.
                it.remove();
            }
        }
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        return super.registerReceiver(receiver, filter, null, mServiceHandler);
    }

    @Override
    public void unregisterReceiver (BroadcastReceiver receiver) {
        super.unregisterReceiver(receiver);
    }

    public void onError(PluginException error) {
        Message msg = Message.obtain(null, MSG_ERROR);
        msg.setData(error.toBundle());
        sendClientMessage(msg);
    }

    public Config getConfig() {
        if (mConfig == null) {
            ConfigurationDAO dao = DAOFactory.createConfigurationDAO(this);
            try {
                mConfig = dao.retrieveConfiguration();
            } catch (JSONException e) {
                logger.error("Config exception: {}", e.getMessage());
                mConfig = Config.getDefault(); //using default config
            }
        }
        return mConfig;
    }

//    public void setConfig(Config config) {
//        this.mConfig = config;
//    }

    private void runHeadlessTask(Task task) {
        if (mHasBoundClients) { // only run headless task if there are no bound clients (activity)
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

    public static boolean isRunning() {
        return LocationService.isRunning;
    }
}
