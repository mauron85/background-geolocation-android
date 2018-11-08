/*
According to apache license

This is fork of christocracy cordova-plugin-background-geolocation plugin
https://github.com/christocracy/cordova-plugin-background-geolocation

This is a new class
*/

package com.marianhello.bgloc.provider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.provider.Settings;
import android.widget.Toast;

import com.google.android.gms.location.DetectedActivity;
import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.PluginException;
import com.marianhello.bgloc.data.BackgroundActivity;
import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.logging.LoggerManager;
import com.marianhello.utils.Tone;

/**
 * AbstractLocationProvider
 */
public abstract class AbstractLocationProvider implements LocationProvider {

    protected Integer PROVIDER_ID;
    protected Config mConfig;
    protected Context mContext;

    protected ToneGenerator toneGenerator;
    protected org.slf4j.Logger logger;

    private ProviderDelegate mDelegate;

    private Tracker1D mLatitudeTracker, mLongitudeTracker, mAltitudeTracker;
    private boolean mPredicted;
    private static final double DEG_TO_METER = 111225.0;
    private static final double METER_TO_DEG = 1.0 / DEG_TO_METER;
    private static final double TIME_STEP = 1.0;
    private static final double COORDINATE_NOISE = 4.0 * METER_TO_DEG;
    private static final double ALTITUDE_NOISE = 10.0;


    protected AbstractLocationProvider(Context context) {
        mContext = context;
        logger = LoggerManager.getLogger(getClass());
        logger.info("Creating {}", getClass().getSimpleName());
    }

    @Override
    public void onCreate() {
        toneGenerator = new android.media.ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
    }

    @Override
    public void onDestroy() {
        toneGenerator.release();
        toneGenerator = null;
    }

    @Override
    public void onConfigure(Config config) {
        mConfig = config;
    }

    @Override
    public void onCommand(int commandId, int arg1) {
        // override in child class
    }

    public void setDelegate(ProviderDelegate delegate) {
        mDelegate = delegate;
    }

    /**
     * Register broadcast reciever
     * @param receiver
     */
    protected Intent registerReceiver (BroadcastReceiver receiver, IntentFilter filter) {
        return mContext.registerReceiver(receiver, filter);
    }

    /**
     * Unregister broadcast reciever
     * @param receiver
     */
    protected void unregisterReceiver (BroadcastReceiver receiver) {
        mContext.unregisterReceiver(receiver);
    }

    /**
     * Handle location as recorder by provider
     * @param location
     */
    protected void handleLocation (Location location) {
        playDebugTone(Tone.BEEP);
        if (mDelegate != null) {
            BackgroundLocation bgLocation = new BackgroundLocation(PROVIDER_ID, location);
            bgLocation.setMockLocationsEnabled(hasMockLocationsEnabled());
            mDelegate.onLocation(bgLocation);
        }
    }

    /**
     * Handle stationary location with radius
     *
     * @param location
     * @param radius radius of stationary region
     */
    protected void handleStationary (Location location, float radius) {
        playDebugTone(Tone.LONG_BEEP);
        if (mDelegate != null) {
            BackgroundLocation bgLocation = new BackgroundLocation(PROVIDER_ID, location);
            bgLocation.setRadius(radius);
            bgLocation.setMockLocationsEnabled(hasMockLocationsEnabled());
            mDelegate.onStationary(bgLocation);
        }
    }

    /**
     * Handle stationary location without radius
     *
     * @param location
     */
    protected void handleStationary (Location location) {
        playDebugTone(Tone.LONG_BEEP);
        if (mDelegate != null) {
            BackgroundLocation bgLocation = new BackgroundLocation(PROVIDER_ID, location);
            bgLocation.setMockLocationsEnabled(hasMockLocationsEnabled());
            mDelegate.onStationary(bgLocation);
        }
    }

    protected void handleActivity(DetectedActivity activity) {
        if (mDelegate != null) {
            mDelegate.onActivity(new BackgroundActivity(PROVIDER_ID, activity));
        }
    }

    /**
     * Handle security exception
     * @param exception
     */
    protected void handleSecurityException (SecurityException exception) {
        PluginException error = new PluginException(exception.getMessage(), PluginException.PERMISSION_DENIED_ERROR);
        if (mDelegate != null) {
            mDelegate.onError(error);
        }
    }

    protected void showDebugToast (String text) {
        if (mConfig.isDebugging()) {
            Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
        }
    }

    public Boolean hasMockLocationsEnabled() {
        return Settings.Secure.getString(mContext.getContentResolver(), android.provider.Settings.Secure.ALLOW_MOCK_LOCATION).equals("1");
    }

    /**
     * Plays debug sound
     * @param name toneGenerator
     */
    protected void playDebugTone (int name) {
        if (toneGenerator == null || !mConfig.isDebugging()) return;

        int duration = 1000;

        toneGenerator.startTone(name, duration);
    }

    /**
     * Apply Kalman filter to location as recorder by provider
     * @param location
     */
    protected Location applyKalmanFilter(Location location) {
        final double accuracy = location.getAccuracy();
        double position, noise;

        // Latitude
        position = location.getLatitude();
        noise = accuracy * METER_TO_DEG;
        if (mLatitudeTracker == null) {
            mLatitudeTracker = new Tracker1D(TIME_STEP, COORDINATE_NOISE);
            mLatitudeTracker.setState(position, 0.0, noise);
        }

        if (!mPredicted)
            mLatitudeTracker.predict(0.0);

        mLatitudeTracker.update(position, noise);

        // Longitude
        position = location.getLongitude();
        noise = accuracy * Math.cos(Math.toRadians(location.getLatitude())) * METER_TO_DEG ;

        if (mLongitudeTracker == null) {

            mLongitudeTracker = new Tracker1D(TIME_STEP, COORDINATE_NOISE);
            mLongitudeTracker.setState(position, 0.0, noise);
        }

        if (!mPredicted)
            mLongitudeTracker.predict(0.0);

        mLongitudeTracker.update(position, noise);

        // Altitude
        if (location.hasAltitude()) {

            position = location.getAltitude();
            noise = accuracy;

            if (mAltitudeTracker == null) {

                mAltitudeTracker = new Tracker1D(TIME_STEP, ALTITUDE_NOISE);
                mAltitudeTracker.setState(position, 0.0, noise);
            }

            if (!mPredicted)
                mAltitudeTracker.predict(0.0);

            mAltitudeTracker.update(position, noise);
        }

        // Reset predicted flag
        mPredicted = false;

        // Latitude
        mLatitudeTracker.predict(0.0);
        location.setLatitude(mLatitudeTracker.getPosition());

        // Longitude
        mLongitudeTracker.predict(0.0);
        location.setLongitude(mLongitudeTracker.getPosition());

        // Altitude
        if (lastLocation != null && lastLocation.hasAltitude()) {
            mAltitudeTracker.predict(0.0);
            location.setAltitude(mAltitudeTracker.getPosition());
        }

        // Speed
        if (lastLocation != null && lastLocation.hasSpeed())
            location.setSpeed(lastLocation.getSpeed());

        // Bearing
        if (lastLocation != null && lastLocation.hasBearing())
            location.setBearing(lastLocation.getBearing());

        // Accuracy (always has)
        location.setAccuracy((float) (mLatitudeTracker.getAccuracy() * DEG_TO_METER));

        // Set times
        location.setTime(System.currentTimeMillis());

        logger.debug("Location after applying kalman filter: {}", location.toString());
        return  location;
    }
}
