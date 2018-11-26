package com.marianhello.bgloc;

import android.content.Intent;
import android.os.IBinder;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;

import java.util.concurrent.TimeoutException;

public abstract class TestLocationServiceHelper {
    private static int MAX_ITERATION = 100;

    public static LocationService getBoundedService(ServiceTestRule serviceRule) throws TimeoutException {
        IBinder binder = null;
        int it = 0;

        // Create the service Intent.
        Intent serviceIntent =
                new Intent(InstrumentationRegistry.getTargetContext(),
                        LocationService.class);

        // due a "bug" in ServiceTestRule we need to run in loop
        while (binder == null && it < MAX_ITERATION) {
            // Bind the service and grab a reference to the binder.
            binder = serviceRule.bindService(serviceIntent);
            it++;
        }

        // Get the reference to the service, or you can call
        // public methods on the binder directly.
        return ((LocationService.LocalBinder) binder).getService();
    }
}
