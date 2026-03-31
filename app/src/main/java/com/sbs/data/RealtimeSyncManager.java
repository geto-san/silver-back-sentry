package com.sbs.data;

import android.content.Context;
import android.text.TextUtils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.sbs.data.local.AppNotificationEntity;
import com.sbs.data.local.HealthObservationEntity;
import com.sbs.data.local.PatrolLogEntity;
import com.sbs.data.local.SightingEntity;
import com.sbs.notifications.AppNotificationHelper;

import java.util.ArrayList;
import java.util.List;

public final class RealtimeSyncManager {

    private static volatile RealtimeSyncManager instance;

    private final Context appContext;
    private ListenerRegistration sharedRecordsListener;
    private ListenerRegistration notificationsListener;
    private String activeUserId;

    private RealtimeSyncManager(Context context) {
        appContext = context.getApplicationContext();
    }

    public static RealtimeSyncManager getInstance(Context context) {
        if (instance == null) {
            synchronized (RealtimeSyncManager.class) {
                if (instance == null) {
                    instance = new RealtimeSyncManager(context);
                }
            }
        }
        return instance;
    }

    public void start() {
        String userId = FirebaseAuth.getInstance().getUid();
        if (TextUtils.isEmpty(userId)) {
            stop();
            return;
        }
        if (userId.equals(activeUserId) && sharedRecordsListener != null && notificationsListener != null) {
            return;
        }
        stop();
        activeUserId = userId;
        AppRepository repository = AppRepository.getInstance(appContext);

        sharedRecordsListener = FirebaseFirestore.getInstance()
                .collection("shared_records")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        return;
                    }
                    List<SightingEntity> sightings = new ArrayList<>();
                    List<PatrolLogEntity> patrolLogs = new ArrayList<>();
                    List<HealthObservationEntity> healthObservations = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        String type = doc.getString("type");
                        String recordId = doc.getString("recordId");
                        if (TextUtils.isEmpty(type) || TextUtils.isEmpty(recordId)) {
                            continue;
                        }
                        long createdAt = doc.getLong("createdAt") == null ? 0L : doc.getLong("createdAt");
                        long updatedAt = doc.getLong("updatedAt") == null ? createdAt : doc.getLong("updatedAt");
                        String authorId = doc.getString("authorId");
                        String authorDisplayName = doc.getString("authorDisplayName");
                        String title = doc.getString("title");
                        String summary = doc.getString("summary");
                        if (RecordType.SIGHTING.equals(type)) {
                            sightings.add(new SightingEntity(
                                    recordId,
                                    doc.getId(),
                                    authorId == null ? "" : authorId,
                                    authorDisplayName,
                                    title,
                                    summary,
                                    doc.getDouble("latitude") == null ? 0.0 : doc.getDouble("latitude"),
                                    doc.getDouble("longitude") == null ? 0.0 : doc.getDouble("longitude"),
                                    createdAt,
                                    doc.getDouble("radius") == null ? 0f : doc.getDouble("radius").floatValue(),
                                    doc.getString("audioPath"),
                                    doc.getString("imagePath"),
                                    doc.getString("videoPath"),
                                    SyncState.SYNCED,
                                    updatedAt,
                                    updatedAt
                            ));
                        } else if (RecordType.PATROL_LOG.equals(type)) {
                            patrolLogs.add(new PatrolLogEntity(
                                    recordId,
                                    doc.getId(),
                                    authorId == null ? "" : authorId,
                                    authorDisplayName,
                                    title,
                                    summary,
                                    createdAt,
                                    doc.getString("audioPath"),
                                    doc.getString("videoPath"),
                                    SyncState.SYNCED,
                                    updatedAt,
                                    updatedAt
                            ));
                        } else if (RecordType.HEALTH_OBSERVATION.equals(type)) {
                            healthObservations.add(new HealthObservationEntity(
                                    recordId,
                                    doc.getId(),
                                    authorId,
                                    authorDisplayName,
                                    title,
                                    summary,
                                    createdAt,
                                    doc.getDouble("latitude") == null ? 0.0 : doc.getDouble("latitude"),
                                    doc.getDouble("longitude") == null ? 0.0 : doc.getDouble("longitude"),
                                    SyncState.SYNCED,
                                    updatedAt,
                                    updatedAt
                            ));
                        }
                    }
                    repository.runOnIo(() -> {
                        repository.mergeRemoteSightings(sightings);
                        repository.mergeRemotePatrolLogs(patrolLogs);
                        repository.mergeRemoteHealthObservations(healthObservations);
                    });
                });

        notificationsListener = FirebaseFirestore.getInstance()
                .collection("notifications")
                .document(userId)
                .collection("items")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        return;
                    }
                    List<AppNotificationEntity> items = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        items.add(new AppNotificationEntity(
                                doc.getId(),
                                userId,
                                doc.getString("actorUserId"),
                                doc.getString("actorName"),
                                doc.getString("recordId"),
                                doc.getString("recordType"),
                                doc.getString("title"),
                                doc.getString("message"),
                                doc.getLong("createdAt") == null ? System.currentTimeMillis() : doc.getLong("createdAt"),
                                Boolean.TRUE.equals(doc.getBoolean("isRead")),
                                doc.getString("destination"),
                                false
                        ));
                    }
                    repository.runOnIo(() -> {
                        repository.mergeNotifications(items);
                        for (AppNotificationEntity pending : repository.getPendingSystemNotifications(userId)) {
                            AppNotificationHelper.showRecordNotification(appContext, new AppNotificationRecord(
                                    pending.notificationId,
                                    pending.recipientUserId,
                                    pending.actorUserId,
                                    pending.actorName,
                                    pending.recordId,
                                    pending.recordType,
                                    pending.title,
                                    pending.message,
                                    pending.createdAt,
                                    pending.isRead,
                                    pending.destination
                            ));
                            repository.markNotificationSystemNotified(pending.notificationId);
                        }
                    });
                });
    }

    public void stop() {
        if (sharedRecordsListener != null) {
            sharedRecordsListener.remove();
            sharedRecordsListener = null;
        }
        if (notificationsListener != null) {
            notificationsListener.remove();
            notificationsListener = null;
        }
        activeUserId = null;
    }
}
