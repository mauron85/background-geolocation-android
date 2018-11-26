package com.marianhello.bgloc.data;

import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.provider.LocationProvider;
import com.marianhello.bgloc.provider.LocationProviderFactory;
import com.marianhello.bgloc.provider.MockLocationProvider;

public class TestLocationProviderFactory extends LocationProviderFactory {
    public TestLocationProviderFactory() {
        super(null);
    }

    public LocationProvider getInstance (Integer locationProvider) {
        LocationProvider provider;
        switch (locationProvider) {
            case Config.DISTANCE_FILTER_PROVIDER:
                throw new IllegalArgumentException("Provider not supported in this factory");
            case Config.ACTIVITY_PROVIDER:
                throw new IllegalArgumentException("Provider not supported in this factory");
            case Config.RAW_PROVIDER:
                throw new IllegalArgumentException("Provider not supported in this factory");
            case 99:
                provider = new MockLocationProvider();
                break;
            default:
                throw new IllegalArgumentException("Provider not found");
        }

        return provider;
    }
}
