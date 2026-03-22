package com.sbs.databinding;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREFS_NAME = "silverback_prefs";
    private static final String KEY_HAS_OPENED_BEFORE = "has_opened_before";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";

    private final SharedPreferences preferences;

    public SessionManager(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean hasOpenedBefore() {
        return preferences.getBoolean(KEY_HAS_OPENED_BEFORE, false);
    }

    public void setHasOpenedBefore(boolean value) {
        preferences.edit().putBoolean(KEY_HAS_OPENED_BEFORE, value).apply();
    }

    public boolean isLoggedIn() {
        return preferences.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public void setLoggedIn(boolean value) {
        preferences.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply();
    }

    public void logout() {
        preferences.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply();
    }
}
