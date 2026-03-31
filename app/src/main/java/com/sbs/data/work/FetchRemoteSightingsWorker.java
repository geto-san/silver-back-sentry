package com.sbs.data.work;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.sbs.data.AppRepository;
import com.sbs.data.RecordType;
import com.sbs.data.SyncState;
import com.sbs.data.local.AppNotificationEntity;
import com.sbs.data.local.HealthObservationEntity;
import com.sbs.data.local.PatrolLogEntity;
import com.sbs.data.local.SightingEntity;

import java.util.ArrayList;
import java.util.List;

public final class FetchRemoteSightingsWorker extends Worker {

    public FetchRemoteSightingsWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            AppRepository repository = AppRepository.getInstance(getApplicationContext());
            List<SightingEntity> sightings = new ArrayList<>();
            List<PatrolLogEntity> patrolLogs = new ArrayList<>();
            List<HealthObservationEntity> healthObservations = new ArrayList<>();

            for (QueryDocumentSnapshot doc : Tasks.await(FirebaseFirestore.getInstance().collection("shared_records").get())) {
                String type = doc.getString("type");
                String recordId = doc.getString("recordId");
                if (TextUtils.isEmpty(type) || TextUtils.isEmpty(recordId)) {
                    continue;
                }
                long createdAt = doc.getLong("createdAt") == null ? 0L : doc.getLong("createdAt");
                long updatedAt = doc.getLong("updatedAt") == null ? createdAt : doc.getLong("updatedAt");
                if (RecordType.SIGHTING.equals(type)) {
                    sightings.add(new SightingEntity(
                            recordId,
                            doc.getId(),
                            safe(doc.getString("authorId")),
                            doc.getString("authorDisplayName"),
                            doc.getString("title"),
                            doc.getString("summary"),
                            value(doc.getDouble("latitude")),
                            value(doc.getDouble("longitude")),
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
                            safe(doc.getString("authorId")),
                            doc.getString("authorDisplayName"),
                            doc.getString("title"),
                            doc.getString("summary"),
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
                            doc.getString("authorId"),
                            doc.getString("authorDisplayName"),
                            doc.getString("title"),
                            doc.getString("summary"),
                            createdAt,
                            value(doc.getDouble("latitude")),
                            value(doc.getDouble("longitude")),
                            SyncState.SYNCED,
                            updatedAt,
                            updatedAt
                    ));
                }
            }

            repository.mergeRemoteSightings(sightings);
            repository.mergeRemotePatrolLogs(patrolLogs);
            repository.mergeRemoteHealthObservations(healthObservations);

            String userId = FirebaseAuth.getInstance().getUid();
            if (!TextUtils.isEmpty(userId)) {
                List<AppNotificationEntity> notifications = new ArrayList<>();
                for (QueryDocumentSnapshot doc : Tasks.await(FirebaseFirestore.getInstance()
                        .collection("notifications")
                        .document(userId)
                        .collection("items")
                        .get())) {
                    notifications.add(new AppNotificationEntity(
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
                repository.mergeNotifications(notifications);
            }
            return Result.success();
        } catch (Exception ignored) {
            return Result.retry();
        }
    }

    private static double value(Double value) {
        return value == null ? 0.0 : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
