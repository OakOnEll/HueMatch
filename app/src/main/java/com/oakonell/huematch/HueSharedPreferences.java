package com.oakonell.huematch;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.google.gson.Gson;
import com.oakonell.huematch.utils.ScreenSection;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HueSharedPreferences {
    private static final String HUE_SHARED_PREFERENCES_STORE = "HueSharedPrefs";
    private static final String LAST_CONNECTED_USERNAME = "LastConnectedUsername";
    private static final String LAST_CONNECTED_IP = "LastConnectedIP";
    private static final String TRANSITION_TIME = "transitionTime";
    private static final String VIEW_FPS = "viewFPS";
    private static final String DEBUGABBLE = "debuggable";
    private static final int DEFAULT_TRANSITION_TIME = 4;


    private static final String CONTROLLED_LIGHT_IDS = "controlledLightIds";
    private static final String LIGHT_SECTIONS_BY_ID = "lightSectionsById";

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

    public Map<String, ScreenSection> getLightSections() {
        final String string = mSharedPreferences.getString(LIGHT_SECTIONS_BY_ID, null);
        if (string == null) return Collections.emptyMap();
        final Gson gson = new Gson();
        Map<String, String> tempMap = gson.fromJson(string, HashMap.class);

        Map<String, ScreenSection> result = new HashMap<>();
        for (Map.Entry<String, String> entry : tempMap.entrySet()) {
            String value = entry.getValue();
            ScreenSection section = ScreenSection.valueOf(ScreenSection.class, value);
            result.put(entry.getKey(), section);
        }
        return result;
    }

    public boolean setLightSections(Map<String, ScreenSection> lightSections) {
        final Gson gson = new Gson();
        String serializedObject = gson.toJson(lightSections);

        mSharedPreferencesEditor.putString(LIGHT_SECTIONS_BY_ID, serializedObject);
        return mSharedPreferencesEditor.commit();
    }


    public int getTransitionTime() {
        return mSharedPreferences.getInt(TRANSITION_TIME, DEFAULT_TRANSITION_TIME);
    }

    public boolean setTransitionTime(int timeMs) {
        mSharedPreferencesEditor.putInt(TRANSITION_TIME, timeMs);
        return mSharedPreferencesEditor.commit();
    }

    public boolean getViewFPS() {
        return mSharedPreferences.getBoolean(VIEW_FPS, false);
    }

    public boolean setViewFPS(boolean value) {
        mSharedPreferencesEditor.putBoolean(VIEW_FPS, value);
        return mSharedPreferencesEditor.commit();
    }

    public boolean getDebuggable() {
        return mSharedPreferences.getBoolean(DEBUGABBLE, false);
    }

    public boolean setDebuggable(boolean value) {
        mSharedPreferencesEditor.putBoolean(DEBUGABBLE, value);
        return mSharedPreferencesEditor.commit();
    }

}