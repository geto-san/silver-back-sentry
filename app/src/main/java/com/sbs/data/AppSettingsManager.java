package com.sbs.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public class AppSettingsManager {

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    public static final String SYNC_INTERVAL_15 = "15 min";
    public static final String SYNC_INTERVAL_30 = "30 min";
    public static final String SYNC_INTERVAL_60 = "60 min";
    public static final String SYNC_INTERVAL_MANUAL = "Manual only";

    private static final String PREFS = "sbs_app_settings";

    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_AUTO_SYNC = "auto_sync";
    private static final String KEY_WIFI_ONLY_SYNC = "wifi_only_sync";
    private static final String KEY_SYNC_INTERVAL = "sync_interval";
    private static final String KEY_AUTO_CENTER_MAP = "auto_center_map";
    private static final String KEY_SHOW_SAMPLE_MARKERS = "show_sample_markers";

    private final SharedPreferences prefs;

    public AppSettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getThemeMode() {
        return prefs.getString(KEY_THEME_MODE, THEME_SYSTEM);
    }

    public void setThemeMode(String themeMode) {
        prefs.edit().putString(KEY_THEME_MODE, themeMode).apply();
    }

    public void applyTheme() {
        String mode = getThemeMode();
        switch (mode) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    public boolean isAutoSyncEnabled() {
        return prefs.getBoolean(KEY_AUTO_SYNC, true);
    }

    public void setAutoSyncEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply();
    }

    public boolean isWifiOnlySyncEnabled() {
        return prefs.getBoolean(KEY_WIFI_ONLY_SYNC, true);
    }

    public void setWifiOnlySyncEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY_SYNC, enabled).apply();
    }

    public String getSyncInterval() {
        return prefs.getString(KEY_SYNC_INTERVAL, SYNC_INTERVAL_30);
    }

    public void setSyncInterval(String interval) {
        prefs.edit().putString(KEY_SYNC_INTERVAL, interval).apply();
    }

    public boolean isAutoCenterMapEnabled() {
        return prefs.getBoolean(KEY_AUTO_CENTER_MAP, true);
    }

    public void setAutoCenterMapEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_CENTER_MAP, enabled).apply();
    }

    public boolean isShowSampleMarkersEnabled() {
        return prefs.getBoolean(KEY_SHOW_SAMPLE_MARKERS, true);
    }

    public void setShowSampleMarkersEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHOW_SAMPLE_MARKERS, enabled).apply();
    }
}
