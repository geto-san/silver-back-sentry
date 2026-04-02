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

public final class SightingStore {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SYNCED = "SYNCED";
    public static final String STATUS_FAILED = "FAILED";

    private static final String PREFS = "sbs_sightings";
    private static final String KEY_SIGHTINGS = "sightings_v3";

    private SightingStore() {}

    public static SightingRecord createSighting(
            Context context,
            String title,
            String notes,
            double lat,
            double lng,
            long timestamp,
            String authorId,
            String authorName,
            String audioPath,
            String imagePath,
            String videoPath,
            float radius
    ) {
        String localId = UUID.randomUUID().toString();
        SightingRecord record = new SightingRecord(
                localId,
                null,
                title,
                notes,
                lat,
                lng,
                timestamp,
                authorId,
                authorName,
                STATUS_PENDING,
                0L,
                audioPath,
                imagePath,
                videoPath,
                radius
        );
        appendRecord(context, record);
        return record;
    }

    public static void updateSighting(Context context, SightingRecord record) {
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

    public static void deleteSighting(Context context, String localId) {
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

    public static List<SightingRecord> getAll(Context context) {
        List<SightingRecord> records = readAll(context);
        Collections.sort(records, Comparator.comparingLong(r -> -r.timestamp));
        return records;
    }

    public static SightingRecord getById(Context context, String localId) {
        for (SightingRecord record : readAll(context)) {
            if (record.localId.equals(localId)) {
                return record;
            }
        }
        return null;
    }

    public static List<SightingRecord> getByStatus(Context context, String... statuses) {
        List<SightingRecord> records = new ArrayList<>();
        List<String> statusList = new ArrayList<>();
        Collections.addAll(statusList, statuses);
        for (SightingRecord record : readAll(context)) {
            if (statusList.contains(record.syncStatus)) {
                records.add(record);
            }
        }
        return records;
    }

    public static int getPendingCount(Context context) {
        return getByStatus(context, STATUS_PENDING, STATUS_FAILED).size();
    }

    public static void updateSyncStatus(
            Context context,
            String localId,
            String firestoreId,
            String status,
            long lastSyncAttempt
    ) {
        JSONArray array = readArray(context);
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject obj = array.getJSONObject(i);
                if (localId.equals(obj.optString("localId"))) {
                    obj.put("syncStatus", status);
                    obj.put("lastSyncAttempt", lastSyncAttempt);
                    if (firestoreId != null) {
                        obj.put("firestoreId", firestoreId);
                    }
                    array.put(i, obj);
                    persistArray(context, array);
                    return;
                }
            } catch (JSONException ignored) {
            }
        }
    }

    public static void mergeRemoteRecords(Context context, List<SightingRecord> remoteRecords) {
        List<SightingRecord> localRecords = readAll(context);
        for (SightingRecord remote : remoteRecords) {
            boolean found = false;
            for (int i = 0; i < localRecords.size(); i++) {
                SightingRecord local = localRecords.get(i);
                if ((remote.firestoreId != null && remote.firestoreId.equals(local.firestoreId)) ||
                    (remote.localId != null && remote.localId.equals(local.localId))) {
                    // Update local if remote is newer or just overwrite
                    localRecords.set(i, remote);
                    found = true;
                    break;
                }
            }
            if (!found) {
                localRecords.add(remote);
            }
        }
        JSONArray array = new JSONArray();
        for (SightingRecord r : localRecords) array.put(toJson(r));
        persistArray(context, array);
    }

    private static void appendRecord(Context context, SightingRecord record) {
        JSONArray array = readArray(context);
        array.put(toJson(record));
        persistArray(context, array);
    }

    private static List<SightingRecord> readAll(Context context) {
        JSONArray array = readArray(context);
        List<SightingRecord> records = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject obj = array.getJSONObject(i);
                records.add(fromJson(obj));
            } catch (JSONException ignored) {
            }
        }
        return records;
    }

    private static JSONArray readArray(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_SIGHTINGS, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static void persistArray(Context context, JSONArray array) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SIGHTINGS, array.toString())
                .apply();
    }

    private static JSONObject toJson(SightingRecord record) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("localId", record.localId);
            obj.put("firestoreId", record.firestoreId);
            obj.put("title", record.title);
            obj.put("notes", record.notes);
            obj.put("lat", record.lat);
            obj.put("lng", record.lng);
            obj.put("timestamp", record.timestamp);
            obj.put("authorId", record.authorId);
            obj.put("authorName", record.authorName);
            obj.put("syncStatus", record.syncStatus);
            obj.put("lastSyncAttempt", record.lastSyncAttempt);
            obj.put("audioPath", record.audioPath);
            obj.put("imagePath", record.imagePath);
            obj.put("videoPath", record.videoPath);
            obj.put("radius", record.radius);
        } catch (JSONException ignored) {
        }
        return obj;
    }

    private static SightingRecord fromJson(JSONObject obj) {
        return new SightingRecord(
                obj.optString("localId"),
                obj.isNull("firestoreId") ? null : obj.optString("firestoreId"),
                obj.optString("title"),
                obj.optString("notes"),
                obj.optDouble("lat"),
                obj.optDouble("lng"),
                obj.optLong("timestamp"),
                obj.optString("authorId", null),
                obj.optString("authorName", null),
                obj.optString("syncStatus", STATUS_PENDING),
                obj.optLong("lastSyncAttempt", 0L),
                obj.optString("audioPath", null),
                obj.optString("imagePath", null),
                obj.optString("videoPath", null),
                (float) obj.optDouble("radius", 0.0)
        );
    }
}
