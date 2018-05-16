package com.marianhello.bgloc;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.util.concurrent.CountDownLatch;

/**
 * This class is used to wait until a successful connection to the service was established. It
 * then serves as a proxy to original {@link android.content.ServiceConnection} passed by
 * the caller.
 *
 * https://android.googlesource.com/platform/frameworks/testing/+/android-support-test/rules/src/main/java/android/support/test/rule/ServiceTestRule.java
 */
public class ProxyServiceConnection implements ServiceConnection {
    private ServiceConnection mCallerConnection;
    public final CountDownLatch mConnectedLatch = new CountDownLatch(1);

    ProxyServiceConnection(ServiceConnection connection) {
        mCallerConnection = connection;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        // store the service binder to return to the caller

        if (mCallerConnection != null) {
            // pass through everything to the callers ServiceConnection
            mCallerConnection.onServiceConnected(name, service);
        }
        mConnectedLatch.countDown();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        //The process hosting the service has crashed or been killed.

        if (mCallerConnection != null) {
            // pass through everything to the callers ServiceConnection
            mCallerConnection.onServiceDisconnected(name);
        }
    }
}
