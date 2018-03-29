/*
According to apache license

This is fork of christocracy cordova-plugin-background-geolocation plugin
https://github.com/christocracy/cordova-plugin-background-geolocation

This is a new class
*/

package com.marianhello.bgloc.provider;

import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.LocationService;
import com.marianhello.bgloc.provider.LocationProvider;
import com.marianhello.bgloc.provider.RawLocationProvider;
import com.tenforwardconsulting.bgloc.DistanceFilterLocationProvider;
import com.marianhello.bgloc.provider.ActivityRecognitionLocationProvider;
import java.lang.IllegalArgumentException;

/**
 * LocationProviderFactory
 */
public class LocationProviderFactory {

    private LocationService mLocationService;
    private Config mConfig;

    public LocationProviderFactory(LocationService locationService, Config config) {
        this.mLocationService = locationService;
        this.mConfig = config;
    };

    public LocationProvider getInstance (Integer locationProvider) {
        LocationProvider provider;
        switch (locationProvider) {
            case Config.DISTANCE_FILTER_PROVIDER:
                provider = new DistanceFilterLocationProvider(mLocationService, mConfig);
                break;
            case Config.ACTIVITY_PROVIDER:
                provider = new ActivityRecognitionLocationProvider(mLocationService, mConfig);
                break;
            case Config.RAW_PROVIDER:
                provider = new RawLocationProvider(mLocationService, mConfig);
                break;
            default:
                throw new IllegalArgumentException("Provider not found");
        }

        return provider;
    }
}
