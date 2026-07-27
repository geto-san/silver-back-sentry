package com.sbs.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.sbs.data.local.AppDatabase;
import com.sbs.data.local.PatrolLogEntity;
import com.sbs.data.local.RangerEntity;
import com.sbs.data.local.SightingEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

final class LegacyDataImporter {

    private static final String PREFS = "sbs_legacy_import";
    private static final String KEY_DONE = "done";
    private static final String UNKNOWN_RANGER = "legacy_unknown";

    private LegacyDataImporter() {
    }

    static void importIfNeeded(Context context, AppDatabase database) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_DONE, false)) {
            return;
        }

        importSightings(context, database);
        importPatrolLogs(context, database);
        prefs.edit().putBoolean(KEY_DONE, true).apply();
    }

    private static void importSightings(Context context, AppDatabase database) {
        SharedPreferences prefs = context.getSharedPreferences("sbs_sightings", Context.MODE_PRIVATE);
        JSONArray array = parseArray(prefs.getString("sightings_v3", "[]"));
        Set<String> rangerIds = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String rangerId = normalizeRangerId(item.optString("authorId", null));
            rangerIds.add(rangerId);
            database.sightingDao().upsert(new SightingEntity(
                    item.optString("localId"),
                    nullable(item.optString("firestoreId", null)),
                    rangerId,
                    nullable(item.optString("authorName", null)),
                    item.optString("title"),
                    item.optString("notes"),
                    item.optDouble("lat"),
                    item.optDouble("lng"),
                    item.optLong("timestamp"),
                    (float) item.optDouble("radius", 0.0),
                    nullable(item.optString("audioPath", null)),
                    nullable(item.optString("imagePath", null)),
                    nullable(item.optString("videoPath", null)),
                    normalizeStatus(item.optString("syncStatus")),
                    item.optLong("lastSyncAttempt"),
                    item.optLong("timestamp")
            ));
        }
        upsertRangers(database, rangerIds);
        prefs.edit().remove("sightings_v3").apply();
    }

    private static void importPatrolLogs(Context context, AppDatabase database) {
        SharedPreferences prefs = context.getSharedPreferences("sbs_patrol_logs", Context.MODE_PRIVATE);
        JSONArray array = parseArray(prefs.getString("patrol_logs_v1", "[]"));
        Set<String> rangerIds = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String rangerId = normalizeRangerId(item.optString("authorId", null));
            rangerIds.add(rangerId);
            database.patrolLogDao().upsert(new PatrolLogEntity(
                    item.optString("localId"),
                    nullable(item.optString("firestoreId", null)),
                    rangerId,
                    nullable(item.optString("authorName", null)),
                    item.optString("title"),
                    item.optString("notes"),
                    item.optLong("timestamp"),
                    nullable(item.optString("audioPath", null)),
                    nullable(item.optString("videoPath", null)),
                    normalizeStatus(item.optString("syncStatus")),
                    0L,
                    item.optLong("timestamp")
            ));
        }
        upsertRangers(database, rangerIds);
        prefs.edit().remove("patrol_logs_v1").apply();
    }

    private static void upsertRangers(AppDatabase database, Set<String> rangerIds) {
        long now = System.currentTimeMillis();
        for (String rangerId : rangerIds) {
            if (database.rangerDao().countById(rangerId) == 0) {
                database.rangerDao().upsert(new RangerEntity(rangerId, "Imported Ranger", null, now, now));
            }
        }
    }

    private static JSONArray parseArray(String raw) {
        try {
            return new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static String normalizeRangerId(String rangerId) {
        return TextUtils.isEmpty(rangerId) ? UNKNOWN_RANGER : rangerId;
    }

    private static String normalizeStatus(String value) {
        if (SyncState.SYNCED.equals(value) || SyncState.FAILED.equals(value)) {
            return value;
        }
        return SyncState.PENDING;
    }

    private static String nullable(String value) {
        return TextUtils.isEmpty(value) ? null : value;
    }
}
