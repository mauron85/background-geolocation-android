package com.marianhello.bgloc.provider;

import com.marianhello.bgloc.provider.AbstractLocationProvider;

public class MockLocationProvider extends AbstractLocationProvider {

    public MockLocationProvider() {
        super(null);
        PROVIDER_ID = 99;
    }

    @Override
    public void onStart() {

    }

    @Override
    public void onStop() {

    }

    @Override
    public boolean isStarted() {
        return false;
    }
}
