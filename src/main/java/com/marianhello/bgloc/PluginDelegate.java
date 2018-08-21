package com.marianhello.bgloc;

import com.marianhello.bgloc.data.BackgroundActivity;
import com.marianhello.bgloc.data.BackgroundLocation;

/**
 * Created by finch on 27.11.2017.
 */

public interface PluginDelegate {
    void onAuthorizationChanged(int authStatus);
    void onLocationChanged(BackgroundLocation location);
    void onStationaryChanged(BackgroundLocation location);
    void onActitivyChanged(BackgroundActivity activity);
    void onServiceStatusChanged(int status);
    void onAbortRequested();
    void onError(PluginException error);
}
