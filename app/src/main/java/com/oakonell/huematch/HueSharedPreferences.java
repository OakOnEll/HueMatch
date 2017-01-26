package com.oakonell.huematch;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import java.util.HashSet;
import java.util.Set;

public class HueSharedPreferences {
    private static final String HUE_SHARED_PREFERENCES_STORE = "HueSharedPrefs";
    private static final String LAST_CONNECTED_USERNAME = "LastConnectedUsername";
    private static final String LAST_CONNECTED_IP = "LastConnectedIP";

    private static final String CONTROLLED_LIGHT_IDS = "controlledLightIds";

    private static HueSharedPreferences instance = null;
    private SharedPreferences mSharedPreferences = null;

    private Editor mSharedPreferencesEditor = null;

    public static HueSharedPreferences getInstance(Context ctx) {
        if (instance == null) {
            instance = new HueSharedPreferences(ctx);
        }
        return instance;
    }

    @SuppressLint("CommitPrefEdits")
    private HueSharedPreferences(Context appContext) {
        mSharedPreferences = appContext.getSharedPreferences(HUE_SHARED_PREFERENCES_STORE, 0); // 0 - for private mode
        mSharedPreferencesEditor = mSharedPreferences.edit();
    }


    public String getUsername() {
        return mSharedPreferences.getString(LAST_CONNECTED_USERNAME, "");
    }

    public boolean setUsername(String username) {
        mSharedPreferencesEditor.putString(LAST_CONNECTED_USERNAME, username);
        return (mSharedPreferencesEditor.commit());
    }

    public String getLastConnectedIPAddress() {
        return mSharedPreferences.getString(LAST_CONNECTED_IP, "");
    }

    public boolean setLastConnectedIPAddress(String ipAddress) {
        mSharedPreferencesEditor.putString(LAST_CONNECTED_IP, ipAddress);
        return (mSharedPreferencesEditor.commit());
    }

    public Set<String> getControlledLightIds() {
        return mSharedPreferences.getStringSet(CONTROLLED_LIGHT_IDS, new HashSet<String>());
    }

    public boolean setControlledLightIds(Set<String> ids) {
        mSharedPreferencesEditor.putStringSet(CONTROLLED_LIGHT_IDS, ids);
        return mSharedPreferencesEditor.commit();
    }
}