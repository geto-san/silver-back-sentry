package com.sbs.data;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.text.TextUtils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SightingSyncManager {

    private SightingSyncManager() {}

    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    public static void syncAllPending(Context context) {
        if (!isOnline(context)) return;
        
        List<SightingRecord> pendingSightings = SightingStore.getByStatus(context, SightingStore.STATUS_PENDING, SightingStore.STATUS_FAILED);
        for (SightingRecord record : pendingSightings) syncSighting(context, record);
        
        List<PatrolLogRecord> pendingLogs = PatrolLogStore.getAll(context); // Simplified: should filter by status
        for (PatrolLogRecord record : pendingLogs) {
            if (PatrolLogStore.STATUS_PENDING.equals(record.syncStatus) || PatrolLogStore.STATUS_FAILED.equals(record.syncStatus)) {
                syncPatrolLog(context, record);
            }
        }
    }

    public static void syncSighting(Context context, SightingRecord record) {
        String firestoreId = TextUtils.isEmpty(record.firestoreId) ? record.localId : record.firestoreId;
        Map<String, Object> data = new HashMap<>();
        data.put("title", record.title);
        data.put("notes", record.notes);
        data.put("lat", record.lat);
        data.put("lng", record.lng);
        data.put("timestamp", record.timestamp);
        data.put("authorId", record.authorId);
        data.put("authorName", record.authorName);
        data.put("audioPath", record.audioPath);
        data.put("imagePath", record.imagePath);
        data.put("videoPath", record.videoPath);
        data.put("radius", record.radius);
        data.put("updatedAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance().collection("sightings").document(firestoreId).set(data)
                .addOnSuccessListener(unused -> SightingStore.updateSyncStatus(context, record.localId, firestoreId, SightingStore.STATUS_SYNCED, System.currentTimeMillis()))
                .addOnFailureListener(e -> SightingStore.updateSyncStatus(context, record.localId, firestoreId, SightingStore.STATUS_FAILED, System.currentTimeMillis()));
    }

    public static void syncPatrolLog(Context context, PatrolLogRecord record) {
        String firestoreId = TextUtils.isEmpty(record.firestoreId) ? record.localId : record.firestoreId;
        Map<String, Object> data = new HashMap<>();
        data.put("title", record.title);
        data.put("notes", record.notes);
        data.put("timestamp", record.timestamp);
        data.put("authorId", record.authorId);
        data.put("authorName", record.authorName);
        data.put("audioPath", record.audioPath);
        data.put("videoPath", record.videoPath);
        data.put("updatedAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance().collection("patrol_logs").document(firestoreId).set(data)
                .addOnSuccessListener(unused -> PatrolLogStore.updateSyncStatus(context, record.localId, firestoreId, PatrolLogStore.STATUS_SYNCED))
                .addOnFailureListener(e -> PatrolLogStore.updateSyncStatus(context, record.localId, firestoreId, PatrolLogStore.STATUS_FAILED));
    }

    public static String resolveAuthorId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public static String resolveAuthorName() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return null;
        if (!TextUtils.isEmpty(user.getDisplayName())) return user.getDisplayName();
        String email = user.getEmail();
        if (TextUtils.isEmpty(email)) return null;
        int atIndex = email.indexOf('@');
        return atIndex > 0 ? email.substring(0, atIndex) : email;
    }
}
