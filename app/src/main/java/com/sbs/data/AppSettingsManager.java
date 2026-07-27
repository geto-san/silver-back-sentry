package com.sbs.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public class AppSettingsManager {

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    private static final String PREFS = "sbs_app_settings";

    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_AUTO_CENTER_MAP = "auto_center_map";

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

    public boolean isAutoCenterMapEnabled() {
        return prefs.getBoolean(KEY_AUTO_CENTER_MAP, true);
    }

    public void setAutoCenterMapEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_CENTER_MAP, enabled).apply();
    }
}
