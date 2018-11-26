package com.marianhello.bgloc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.v4.content.LocalBroadcastManager;

import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.bgloc.data.TestLocationProviderFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.Is.isA;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsInstanceOf.any;
import static org.junit.Assert.assertThat;

@RunWith(AndroidJUnit4.class)
public class LocationServiceTest {
    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    private LocationService service;

    @Before
    public void setUp() {
        try {
            service = TestLocationServiceHelper.getBoundedService(mServiceRule);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    @After
    public void tearDown() {
        service.stop();
    }

    @Test
    public void testWithStartedService() throws TimeoutException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        BroadcastReceiver serviceBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle bundle = intent.getExtras();
                int action = bundle.getInt("action");

                if (action == LocationService.MSG_ON_SERVICE_STARTED) {
                    latch.countDown();
                }
            }
        };
        LocalBroadcastManager.getInstance(InstrumentationRegistry.getTargetContext())
                .registerReceiver(serviceBroadcastReceiver, new IntentFilter(LocationService.ACTION_BROADCAST));

        //assertThat(service.isBound(), is(false));
        assertThat(service.isStarted(), is(false));
        mServiceRule.startService(
                new Intent(InstrumentationRegistry.getTargetContext(), LocationService.class));

        latch.await();
        assertThat(service.isStarted(), is(true));
    }

    @Test
    public void testWithBoundService() {
        // Verify that the service is working correctly.
        assertThat(service.isStarted(), is(false));
        assertThat(service.isBound(), is(true));
        assertThat(service.getConfig(), is(any(Config.class)));
    }

    @Test
    public void testUnboundService() {
        // Verify that the service is working correctly.
        assertThat(service.isStarted(), is(false));
        assertThat(service.isBound(), is(true));
    }

    @Test
    public void testStartStop() {
        assertThat(service.isStarted(), is(false));
        service.start();
        assertThat(service.isStarted(), is(true));
        service.stop();
        assertThat(service.isStarted(), is(false));
    }

    @Test
    public void testOnLocation() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        BroadcastReceiver serviceBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle bundle = intent.getExtras();
                int action = bundle.getInt("action");

                if (action == LocationService.MSG_ON_LOCATION) {
                    bundle.setClassLoader(LocationService.class.getClassLoader());
                    BackgroundLocation location = (BackgroundLocation) bundle.getParcelable("payload");

                    assertThat(location, isA(BackgroundLocation.class));
                    assertThat(location.getLocationId(), equalTo((long) 666));
                    latch.countDown();
                }
            }
        };

        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(InstrumentationRegistry.getTargetContext());
        lbm.registerReceiver(serviceBroadcastReceiver, new IntentFilter(LocationService.ACTION_BROADCAST));

        service.start();
        BackgroundLocation location = new BackgroundLocation();
        location.setLocationId((long) 666);
        service.onLocation(location);

        latch.await();
        lbm.unregisterReceiver(serviceBroadcastReceiver);
    }

    @Test
    public void testOnLocationOnDestroyedService() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        BroadcastReceiver serviceBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle bundle = intent.getExtras();
                int action = bundle.getInt("action");

                if (action == LocationService.MSG_ON_SERVICE_STARTED) {
                    latch.countDown();
                    service.stop();
                    //service.onDestroy();
                    service.onLocation(new BackgroundLocation());
                }
            }
        };

        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(InstrumentationRegistry.getTargetContext());
        lbm.registerReceiver(serviceBroadcastReceiver, new IntentFilter(LocationService.ACTION_BROADCAST));

        Config config = Config.getDefault();
        config.setStartForeground(false);
        config.setLocationProvider(99); // MockLocationProvider

        service.setLocationProviderFactory(new TestLocationProviderFactory());
        service.configure(config);
        service.start();

        latch.await();
        lbm.unregisterReceiver(serviceBroadcastReceiver);
    }
}
