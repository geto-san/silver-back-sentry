package com.sbs.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class FieldDataStore {

    private static final String PREFS = "sbs_field_data";
    private static final String KEY_SIGHTINGS = "sightings";
    private static final String KEY_HEALTH = "health_observations";
    private static final String KEY_LAST_SYNC = "last_sync";

    private FieldDataStore() {}

    public static void saveSighting(Context context, String title, double lat, double lng, String notes) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("title", title);
            obj.put("lat", lat);
            obj.put("lng", lng);
            obj.put("notes", notes);
            obj.put("timestamp", System.currentTimeMillis());
            appendItem(context, KEY_SIGHTINGS, obj);
        } catch (JSONException ignored) {
        }
    }

    public static void saveHealthObservation(Context context, String subject, String severity, String findings, String actionTaken) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("subject", subject);
            obj.put("severity", severity);
            obj.put("findings", findings);
            obj.put("actionTaken", actionTaken);
            obj.put("timestamp", System.currentTimeMillis());
            appendItem(context, KEY_HEALTH, obj);
        } catch (JSONException ignored) {
        }
    }

    public static int getSightingsCount(Context context) {
        return SightingStore.getTotalCount(context);
    }

    public static int getHealthObservationCount(Context context) {
        return getArray(context, KEY_HEALTH).length();
    }

    public static int getPendingItemCount(Context context) {
        return SightingStore.getPendingCount(context) + getHealthObservationCount(context);
    }

    public static String getLastSync(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LAST_SYNC, "Not synced yet");
    }

    public static void setLastSync(Context context, String value) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_SYNC, value)
                .apply();
    }

    private static void appendItem(Context context, String key, JSONObject item) {
        JSONArray array = getArray(context, key);
        array.put(item);

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(key, array.toString())
                .apply();
    }

    private static JSONArray getArray(Context context, String key) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(key, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }
}
