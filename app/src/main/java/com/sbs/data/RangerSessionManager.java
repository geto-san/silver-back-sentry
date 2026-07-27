package com.sbs.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class RangerSessionManager {

    private static final String PREFS = "sbs_ranger_session";
    private static final String KEY_ACTIVE_RANGER_ID = "active_ranger_id";
    private static final String KEY_KNOWN_RANGERS = "known_rangers";

    private final SharedPreferences preferences;

    public RangerSessionManager(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Nullable
    public String getActiveRangerId() {
        return preferences.getString(KEY_ACTIVE_RANGER_ID, null);
    }

    public void setActiveRangerId(@Nullable String rangerId) {
        preferences.edit().putString(KEY_ACTIVE_RANGER_ID, rangerId).apply();
    }

    public void registerKnownRanger(String rangerId) {
        Set<String> known = new HashSet<>(preferences.getStringSet(KEY_KNOWN_RANGERS, new HashSet<>()));
        known.add(rangerId);
        preferences.edit().putStringSet(KEY_KNOWN_RANGERS, known).apply();
    }

    public void removeKnownRanger(String rangerId) {
        Set<String> known = new HashSet<>(preferences.getStringSet(KEY_KNOWN_RANGERS, new HashSet<>()));
        known.remove(rangerId);
        SharedPreferences.Editor editor = preferences.edit().putStringSet(KEY_KNOWN_RANGERS, known);
        if (rangerId.equals(getActiveRangerId())) {
            editor.remove(KEY_ACTIVE_RANGER_ID);
        }
        editor.apply();
    }

    public Set<String> getKnownRangers() {
        return new HashSet<>(preferences.getStringSet(KEY_KNOWN_RANGERS, new HashSet<>()));
    }
}
