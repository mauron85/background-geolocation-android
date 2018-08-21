package com.marianhello.bgloc;

import android.Manifest;
import android.accounts.Account;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;

import com.github.jparkie.promise.Promise;
import com.intentfilter.androidpermissions.PermissionManager;
import com.marianhello.bgloc.data.BackgroundActivity;
import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.bgloc.data.ConfigurationDAO;
import com.marianhello.bgloc.data.DAOFactory;
import com.marianhello.bgloc.data.LocationDAO;
import com.marianhello.bgloc.provider.LocationProvider;
import com.marianhello.bgloc.sync.AccountHelper;
import com.marianhello.bgloc.sync.SyncService;
import com.marianhello.logging.DBLogReader;
import com.marianhello.logging.LogEntry;
import com.marianhello.logging.LoggerManager;
import com.marianhello.logging.UncaughtExceptionLogger;

import org.chromium.content.browser.ThreadUtils;
import org.json.JSONException;
import org.slf4j.event.Level;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BackgroundGeolocationFacade {

    public static final int BACKGROUND_MODE = 0;
    public static final int FOREGROUND_MODE = 1;
    public static final int AUTHORIZATION_AUTHORIZED = 1;
    public static final int AUTHORIZATION_DENIED = 0;
    public static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private Context mContext;
    private LocationService mService = null;
    /** Flag indicating whether we have called bind on the service. */
    private boolean mIsBound = false;
    private ServiceConnection mServiceConnection;
    private long mServiceConnectionTimeout = 10000;
    private TimeUnit mServiceConnectionTimeUnit = TimeUnit.MILLISECONDS;
    private boolean mServiceBroadcastReceiverRegistered = false;
    private boolean mLocationModeChangeReceiverRegistered = false;

    private final Object mLock = new Object();
    private final PluginDelegate mDelegate;

    private boolean mShouldStartService = false;
    private boolean mShouldStopService = false;
    private Config mNextConfiguration = null;

    private BackgroundLocation mStationaryLocation;

    private org.slf4j.Logger logger;

    public BackgroundGeolocationFacade(Context context, PluginDelegate delegate) {
        mContext = context;
        mDelegate = delegate;

        UncaughtExceptionLogger.register(context.getApplicationContext());

        logger = LoggerManager.getLogger(BackgroundGeolocationFacade.class);
        LoggerManager.enableDBLogging();

        logger.info("Initializing plugin");

        NotificationHelper.registerAllChannels(context.getApplicationContext());
    }

    /**
     * Class for interacting with the main interface of the service.
     */
    private class LocationServiceConnection implements ServiceConnection {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            logger.debug("Service connected");
            LocationService.LocalBinder binder = (LocationService.LocalBinder) service;
            mService = binder.getService();
            mIsBound = true;

            if (mNextConfiguration != null)
            {
                mService.configure(mNextConfiguration);
                mNextConfiguration = null;
            }

            if (mShouldStartService)
            {
                start();
            }
            else if (mShouldStopService)
            {
                stop();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            logger.debug("Service disconnected");
            mService = null;
            mIsBound = false;
        }
    };

    private BroadcastReceiver locationModeChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            logger.debug("Authorization has changed");
            mDelegate.onAuthorizationChanged(getAuthorizationStatus());
        }
    };

    private BroadcastReceiver serviceBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            int action = bundle.getInt("action");

            switch (action) {
                case LocationService.MSG_ON_LOCATION: {
                    logger.debug("Received MSG_ON_LOCATION");
                    bundle.setClassLoader(LocationService.class.getClassLoader());
                    BackgroundLocation location = (BackgroundLocation) bundle.getParcelable("payload");
                    mDelegate.onLocationChanged(location);
                    return;
                }

                case LocationService.MSG_ON_STATIONARY: {
                    logger.debug("Received MSG_ON_STATIONARY");
                    bundle.setClassLoader(LocationService.class.getClassLoader());
                    BackgroundLocation location = (BackgroundLocation) bundle.getParcelable("payload");
                    mStationaryLocation = location;
                    mDelegate.onStationaryChanged(location);
                    return;
                }

                case LocationService.MSG_ON_ACTIVITY: {
                    logger.debug("Received MSG_ON_ACTIVITY");
                    bundle.setClassLoader(LocationService.class.getClassLoader());
                    BackgroundActivity activity = (BackgroundActivity) bundle.getParcelable("payload");
                    mDelegate.onActitivyChanged(activity);
                    return;
                }

                case LocationService.MSG_ON_ERROR: {
                    logger.debug("Received MSG_ON_ERROR");
                    Bundle errorBundle = bundle.getBundle("payload");
                    Integer errorCode = errorBundle.getInt("code");
                    String errorMessage = errorBundle.getString("message");
                    mDelegate.onError(new PluginException(errorMessage, errorCode));
                    return;
                }

                case LocationService.MSG_ON_SERVICE_STARTED: {
                    logger.debug("Received MSG_ON_SERVICE_STARTED");
                    mDelegate.onServiceStatusChanged(LocationService.SERVICE_STARTED);
                    return;
                }

                case LocationService.MSG_ON_SERVICE_STOPPED: {
                    logger.debug("Received MSG_ON_SERVICE_STOPPED");
                    mDelegate.onServiceStatusChanged(LocationService.SERVICE_STOPPED);
                    return;
                }

                case LocationService.MSG_ON_ABORT_REQUESTED: {
                    logger.debug("Received MSG_ON_ABORT_REQUESTED");

                    if (mDelegate != null) {
                        // We have a delegate, tell it that there's a request.
                        // It will decide whether to stop or not.
                        mDelegate.onAbortRequested();
                    } else {
                        // No delegate, we may be running in the background.
                        // Let's just stop.
                        stop();
                    }

                    return;
                }
            }
        }
    };

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private synchronized void registerLocationModeChangeReceiver() {
        if (mLocationModeChangeReceiverRegistered) return;

        getContext().registerReceiver(locationModeChangeReceiver, new IntentFilter(android.location.LocationManager.MODE_CHANGED_ACTION));
        mLocationModeChangeReceiverRegistered = true;
    }

    private synchronized void unregisterLocationModeChangeReceiver() {
        if (!mLocationModeChangeReceiverRegistered) return;

        Context context = getContext();
        if (context != null) {
            context.unregisterReceiver(locationModeChangeReceiver);
        }
        mLocationModeChangeReceiverRegistered = false;
    }

    private synchronized void registerServiceBroadcast() {
        if (mServiceBroadcastReceiverRegistered) return;

        LocalBroadcastManager.getInstance(getContext()).registerReceiver(serviceBroadcastReceiver, new IntentFilter(LocationService.ACTION_BROADCAST));
        mServiceBroadcastReceiverRegistered = true;
    }

    private synchronized void unregisterServiceBroadcast() {
        if (!mServiceBroadcastReceiverRegistered) return;

        Context context = getContext();
        if (context != null) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(serviceBroadcastReceiver);
        }

        mServiceBroadcastReceiverRegistered = false;
    }

    public void start() {
        if (mService == null)
        {
            logger.debug("Should start service, but mService is not bound yet.");
            mShouldStartService = true;
            mShouldStopService = false;
            return;
        }

        logger.debug("Starting service");
        mShouldStartService = false;
        mShouldStopService = false;

        PermissionManager permissionManager = PermissionManager.getInstance(getContext());
        permissionManager.checkPermissions(Arrays.asList(PERMISSIONS), new PermissionManager.PermissionRequestListener() {
            @Override
            public void onPermissionGranted() {
                logger.info("User granted requested permissions");
                // watch location mode changes
                registerLocationModeChangeReceiver();
                registerServiceBroadcast();
                startBackgroundService();
            }

            @Override
            public void onPermissionDenied() {
                logger.info("User denied requested permissions");
                if (mDelegate != null) {
                    mDelegate.onAuthorizationChanged(BackgroundGeolocationFacade.AUTHORIZATION_DENIED);
                }
            }
        });
    }

    public void stop() {
        if (mService == null)
        {
            logger.debug("Should stop service, but mService is not bound yet.");
            mShouldStartService = false;
            mShouldStopService = true;
            return;
        }

        logger.debug("Stopping service");
        mShouldStartService = false;
        mShouldStopService = false;
        
        stopBackgroundService();
        unregisterLocationModeChangeReceiver();
//        unregisterServiceBroadcast();
    }

    public void pause() {
        switchMode(BackgroundGeolocationFacade.BACKGROUND_MODE);
        unbindService();
    }

    public void resume() {
        synchronized (mLock) {
            registerServiceBroadcast();
            if (LocationService.isStarted()) {
                registerLocationModeChangeReceiver();
            }

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    try {
                        bindService();
                        switchMode(BackgroundGeolocationFacade.FOREGROUND_MODE);
                    } catch (TimeoutException e) {
                        logger.warn("Connection to service timed out", e);
                    }
                }
            };

            if (ThreadUtils.runningOnUiThread()) {
                new Thread(r).start();
            } else {
                r.run();
            }
        }
    }

    public void destroy() {
        logger.info("Destroying plugin");

        unregisterLocationModeChangeReceiver();
        unregisterServiceBroadcast();

        try {
            if (getConfig().getStopOnTerminate()) {
                stopBackgroundService();
            }
        } catch (PluginException e) {
            logger.debug("Error occurred while destroying plugin", e);
        } finally {
            // Unbind from the service
            unbindService();
        }
    }

    public Collection<BackgroundLocation> getLocations() {
        LocationDAO dao = DAOFactory.createLocationDAO(getContext());
        return dao.getAllLocations();
    }

    public Collection<BackgroundLocation> getValidLocations() {
        LocationDAO dao = DAOFactory.createLocationDAO(getContext());
        return dao.getValidLocations();
    }

    public BackgroundLocation getStationaryLocation() {
        return mStationaryLocation;
    }

    public void deleteLocation(Long locationId) {
        logger.info("Deleting location locationId={}", locationId);
        LocationDAO dao = DAOFactory.createLocationDAO(getContext());
        dao.deleteLocationById(locationId.longValue());
    }

    public void deleteAllLocations() {
        logger.info("Deleting all locations");
        LocationDAO dao = DAOFactory.createLocationDAO(getContext());
        dao.deleteAllLocations();
    }

    public BackgroundLocation getCurrentLocation(int timeout, long maximumAge, boolean enableHighAccuracy) throws PluginException {
        logger.info("Getting current location with timeout:{} maximumAge:{} enableHighAccuracy:{}", timeout, maximumAge, enableHighAccuracy);

        LocationManager locationManager = LocationManager.getInstance(getContext());
        Promise<Location> promise = locationManager.getCurrentLocation(timeout, maximumAge, enableHighAccuracy);
        try {
            promise.await();
            Location location = promise.get();
            if (location != null) {
                return new BackgroundLocation(location);
            }

            Throwable error = promise.getError();
            if (error == null) {
                throw new PluginException("Location not available", 2); // LOCATION_UNAVAILABLE
            }
            if (error instanceof LocationManager.PermissionDeniedException) {
                logger.warn("Getting current location failed due missing permissions");
                throw new PluginException("Permission denied", 1); // PERMISSION_DENIED
            }
            if (error instanceof TimeoutException) {
                throw new PluginException("Location request timed out", 3); // TIME_OUT
            }

            throw new PluginException(error.getMessage(), 2); // LOCATION_UNAVAILABLE
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting location", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting location", e);
        }
    }

    public void switchMode(final int mode) {
        synchronized (mLock) {
            mService.executeProviderCommand(LocationProvider.CMD_SWITCH_MODE, mode);
        }
    }

    public void sendCommand(final int commandId) {
        synchronized (mLock) {
            mService.executeProviderCommand(commandId, 0);
        }
    }

    public void configure(Config config) throws PluginException {
        synchronized (mLock) {
            try
            {
                Config newConfig = Config.merge(getConfig(), config);
                persistConfiguration(newConfig);
                logger.debug("Service configured with: {}", newConfig.toString());

                if (mService != null)
                {
                    mService.configure(newConfig);
                }
                else
                {
                    mNextConfiguration = newConfig;
                }

            } catch (Exception e) {
                logger.error("Configuration error: {}", e.getMessage());
                throw new PluginException("Configuration error", e, PluginException.CONFIGURE_ERROR);
            }
        }
    }

    public Config getConfig() throws PluginException {
        synchronized (mLock) {
            try {
                ConfigurationDAO dao = DAOFactory.createConfigurationDAO(getContext());
                Config config = dao.retrieveConfiguration();
                if (config == null) {
                    config = Config.getDefault();
                }
                return config;
            } catch (JSONException e) {
                logger.error("Error getting stored config: {}", e.getMessage());
                throw new PluginException("Error getting stored config", e, PluginException.JSON_ERROR);
            }
        }
    }

    public Collection<LogEntry> getLogEntries(int limit) {
        DBLogReader logReader = new DBLogReader();
        return logReader.getEntries(limit, 0, Level.DEBUG);
    }

    public Collection<LogEntry> getLogEntries(int limit, int offset, String minLevel) {
        DBLogReader logReader = new DBLogReader();
        return logReader.getEntries(limit, offset, Level.valueOf(minLevel));
    }

    /**
     * Force location sync
     *
     * Method is ignoring syncThreshold and also user sync settings preference
     * and sync locations to defined syncUrl
     */
    public void forceSync() {
        logger.debug("Sync locations forced");
        ResourceResolver resolver = ResourceResolver.newInstance(getContext());
        Account syncAccount = AccountHelper.CreateSyncAccount(getContext(), SyncService.ACCOUNT_NAME,
                resolver.getString(SyncService.ACCOUNT_TYPE_RESOURCE));
        SyncService.sync(syncAccount, resolver.getString(SyncService.AUTHORITY_TYPE_RESOURCE), true);
    }

    public int getAuthorizationStatus() {
        return hasPermissions() ? AUTHORIZATION_AUTHORIZED : AUTHORIZATION_DENIED;
    }

    public boolean hasPermissions() {
        return hasPermissions(getContext(), PERMISSIONS);
    }

    public boolean locationServicesEnabled() throws PluginException {
        Context context = getContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int locationMode = 0;
            try {
                locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
                return locationMode != Settings.Secure.LOCATION_MODE_OFF;
            } catch (SettingNotFoundException e) {
                logger.error("Location services check failed", e);
                throw new PluginException("Location services check failed", e, PluginException.SETTINGS_ERROR);
            }
        } else {
            String locationProviders = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            return !TextUtils.isEmpty(locationProviders);
        }
    }

    public void registerHeadlessTask(final String jsFunction) {
        logger.info("Registering headless task");
        synchronized (mLock) {
            mService.registerHeadlessTask(jsFunction);
        }
    }

    private void startBackgroundService() {
        logger.info("Attempt to start bg service");
        synchronized (mLock) {
            mService.start();
        }
    }

    private void stopBackgroundService() {
        logger.info("Attempt to stop bg service");
        synchronized (mLock) {
            mService.stop();
        }
    }

    public boolean isRunning() {
        return LocationService.isStarted();
    }

    private void bindService() throws TimeoutException {
        ThreadUtils.runOnUiThreadBlocking(new Runnable() {
            @Override
            public void run() {
                unsafeBindService();
            }
        });
        waitOnLatch(((ProxyServiceConnection) mServiceConnection).mConnectedLatch, "connected");
    }

    private void unbindService() {
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                unsafeUnbindService();
            }
        });
    }

    private synchronized void unsafeBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
        if (mIsBound) return;

        logger.debug("Binding to service");

        final Context context = getApplicationContext();
        Intent locationServiceIntent = new Intent(context, LocationService.class);
        mServiceConnection = new ProxyServiceConnection(new LocationServiceConnection());
        context.bindService(locationServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private synchronized void unsafeUnbindService() {
        if (mIsBound == false) return;

        logger.debug("Unbinding from service");
        // If we have received the service, and hence registered with
        // it, then now is the time to unregister.
        if (mService != null) {
            // Detach our existing connection.
            final Context context = getApplicationContext();

            if (context != null) { //workaround for issue RN #9791
                // not unbinding from service will cause ServiceConnectionLeaked
                // but there is not much we can do about it now
                context.unbindService(mServiceConnection);
            }

            mIsBound = false;
        }
    }

    /**
     * Helper method to block on a given latch for the duration of the set timeout
     */
    private void waitOnLatch(CountDownLatch latch, String actionName) throws TimeoutException {
        try {
            if (!latch.await(mServiceConnectionTimeout, mServiceConnectionTimeUnit)) {
                throw new TimeoutException("Waited for " + mServiceConnectionTimeout + " " + mServiceConnectionTimeUnit.name() + "," +
                        " but service was never " + actionName);
            }
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for service to be " + actionName, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for service to be " + actionName, e);
        }
    }

    private void persistConfiguration(Config config) throws NullPointerException {
        ConfigurationDAO dao = DAOFactory.createConfigurationDAO(getContext());
        dao.persistConfiguration(config);
    }

    private Context getContext() {
        return mContext;
    }

    private Context getApplicationContext() {
        return mContext.getApplicationContext();
    }

    public static void showAppSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        context.startActivity(intent);
    }

    public static void showLocationSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        context.startActivity(intent);
    }

    public static boolean hasPermissions(Context context, String[] permissions) {
        for (String perm: permissions) {
            if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Sets a transform for each coordinate about to be committed (sent or saved for later sync).
     * You can use this for modifying the coordinates in any way.
     *
     * If the transform returns <code>null</code>, it will prevent the location from being committed.
     * @param transform - the transform listener
     */
    public static void setLocationTransform(LocationService.ILocationTransform transform) {
        LocationService.setLocationTransform(transform);
    }

    public static LocationService.ILocationTransform getLocationTransform() {
        return LocationService.getLocationTransform();
    }
}
