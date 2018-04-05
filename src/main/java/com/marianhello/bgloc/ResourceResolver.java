package com.marianhello.bgloc;

import android.content.Context;

/**
 * Created by finch on 19/07/16.
 */
public class ResourceResolver {

    private Context mContext;

    private ResourceResolver(Context context) {
        mContext = context;
    }

    private Context getApplicationContext() {
        return mContext.getApplicationContext();
    }

    public int getAppResource(String name, String type) {
        Context appContext = getApplicationContext();
        return appContext.getResources().getIdentifier(name, type, appContext.getPackageName());
    }

    public Integer getDrawable(String resourceName) {
        return getAppResource(resourceName, "drawable");
    }

    public String getString(String name) {
        return getApplicationContext().getString(getAppResource(name, "string"));
    }

    public static ResourceResolver newInstance(Context context) {
        return new ResourceResolver(context);
    }
}
