package com.sbs.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class PatrolLogStore {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SYNCED = "SYNCED";
    public static final String STATUS_FAILED = "FAILED";

    private static final String PREFS = "sbs_patrol_logs";
    private static final String KEY_LOGS = "patrol_logs_v1";

    private PatrolLogStore() {}

    public static PatrolLogRecord createLog(
            Context context,
            String title,
            String notes,
            long timestamp,
            String authorId,
            String authorName,
            String audioPath,
            String videoPath
    ) {
        String localId = UUID.randomUUID().toString();
        PatrolLogRecord record = new PatrolLogRecord(
                localId,
                null,
                title,
                notes,
                timestamp,
                authorId,
                authorName,
                STATUS_PENDING,
                audioPath,
                videoPath
        );
        appendRecord(context, record);
        return record;
    }

    public static void updateLog(Context context, PatrolLogRecord record) {
        JSONArray array = readArray(context);
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject obj = array.getJSONObject(i);
                if (record.localId.equals(obj.optString("localId"))) {
                    array.put(i, toJson(record));
                    persistArray(context, array);
                    return;
                }
            } catch (JSONException ignored) {}
        }
    }

    public static void deleteLog(Context context, String localId) {
        JSONArray array = readArray(context);
        JSONArray next = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject obj = array.getJSONObject(i);
                if (!localId.equals(obj.optString("localId"))) {
                    next.put(obj);
                }
            } catch (JSONException ignored) {}
        }
        persistArray(context, next);
    }

    public static List<PatrolLogRecord> getAll(Context context) {
        List<PatrolLogRecord> records = readAll(context);
        Collections.sort(records, Comparator.comparingLong(r -> -r.timestamp));
        return records;
    }

    public static PatrolLogRecord getById(Context context, String localId) {
        for (PatrolLogRecord record : readAll(context)) {
            if (record.localId.equals(localId)) {
                return record;
            }
        }
        return null;
    }

    public static void updateSyncStatus(Context context, String localId, String firestoreId, String status) {
        JSONArray array = readArray(context);
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject obj = array.getJSONObject(i);
                if (localId.equals(obj.optString("localId"))) {
                    obj.put("syncStatus", status);
                    if (firestoreId != null) obj.put("firestoreId", firestoreId);
                    array.put(i, obj);
                    persistArray(context, array);
                    return;
                }
            } catch (JSONException ignored) {}
        }
    }

    private static void appendRecord(Context context, PatrolLogRecord record) {
        JSONArray array = readArray(context);
        array.put(toJson(record));
        persistArray(context, array);
    }

    private static List<PatrolLogRecord> readAll(Context context) {
        JSONArray array = readArray(context);
        List<PatrolLogRecord> records = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject obj = array.getJSONObject(i);
                records.add(fromJson(obj));
            } catch (JSONException ignored) {}
        }
        return records;
    }

    private static JSONArray readArray(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return parseArray(prefs.getString(KEY_LOGS, "[]"));
    }

    private static JSONArray parseArray(String raw) {
        try { return new JSONArray(raw); } catch (JSONException e) { return new JSONArray(); }
    }

    private static void persistArray(Context context, JSONArray array) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LOGS, array.toString()).apply();
    }

    private static JSONObject toJson(PatrolLogRecord record) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("localId", record.localId);
            obj.put("firestoreId", record.firestoreId);
            obj.put("title", record.title);
            obj.put("notes", record.notes);
            obj.put("timestamp", record.timestamp);
            obj.put("authorId", record.authorId);
            obj.put("authorName", record.authorName);
            obj.put("syncStatus", record.syncStatus);
            obj.put("audioPath", record.audioPath);
            obj.put("videoPath", record.videoPath);
        } catch (JSONException ignored) {}
        return obj;
    }

    private static PatrolLogRecord fromJson(JSONObject obj) {
        return new PatrolLogRecord(
                obj.optString("localId"),
                obj.isNull("firestoreId") ? null : obj.optString("firestoreId"),
                obj.optString("title"),
                obj.optString("notes"),
                obj.optLong("timestamp"),
                obj.optString("authorId"),
                obj.optString("authorName"),
                obj.optString("syncStatus"),
                obj.optString("audioPath"),
                obj.optString("videoPath")
        );
    }
}
