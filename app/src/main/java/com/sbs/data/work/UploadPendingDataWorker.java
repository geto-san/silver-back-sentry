package com.sbs.data.work;

import android.content.Context;
import android.os.BatteryManager;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.sbs.data.AppRepository;
import com.sbs.data.RecordType;
import com.sbs.data.local.HealthObservationEntity;
import com.sbs.data.local.PatrolLogEntity;
import com.sbs.data.local.SightingEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class UploadPendingDataWorker extends Worker {

    private static final int BATCH_LIMIT = 200;

    public UploadPendingDataWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (getBatteryPercent() < 20) {
            return Result.retry();
        }

        AppRepository repository = AppRepository.getInstance(getApplicationContext());
        try {
            syncSightings(repository.getPendingSightings(BATCH_LIMIT), repository);
            syncPatrolLogs(repository.getPendingPatrolLogs(BATCH_LIMIT), repository);
            syncHealthObservations(repository.getPendingHealthObservations(BATCH_LIMIT), repository);
            return Result.success();
        } catch (Exception ignored) {
            return Result.retry();
        }
    }

    private void syncSightings(List<SightingEntity> entities, AppRepository repository) throws Exception {
        for (SightingEntity entity : entities) {
            repository.markSightingSyncing(entity);
            try {
                writeSharedRecord(entity.localId, buildSightingPayload(entity));
                fanOutNotification(entity.localId, RecordType.SIGHTING, entity.rangerId, entity.authorName, entity.title, notificationMessage(RecordType.SIGHTING, entity.authorName));
                repository.markSightingSynced(entity, entity.localId);
            } catch (Exception e) {
                repository.markSightingFailed(entity);
                throw e;
            }
        }
    }

    private void syncPatrolLogs(List<PatrolLogEntity> entities, AppRepository repository) throws Exception {
        for (PatrolLogEntity entity : entities) {
            repository.markPatrolLogSyncing(entity);
            try {
                writeSharedRecord(entity.localId, buildPatrolPayload(entity));
                fanOutNotification(entity.localId, RecordType.PATROL_LOG, entity.rangerId, entity.authorName, entity.title, notificationMessage(RecordType.PATROL_LOG, entity.authorName));
                repository.markPatrolLogSynced(entity, entity.localId);
            } catch (Exception e) {
                repository.markPatrolLogFailed(entity);
                throw e;
            }
        }
    }

    private void syncHealthObservations(List<HealthObservationEntity> entities, AppRepository repository) throws Exception {
        for (HealthObservationEntity entity : entities) {
            repository.markHealthObservationSyncing(entity);
            try {
                writeSharedRecord(entity.localId, buildHealthPayload(entity));
                fanOutNotification(entity.localId, RecordType.HEALTH_OBSERVATION, entity.authorId, entity.authorName, entity.title, notificationMessage(RecordType.HEALTH_OBSERVATION, entity.authorName));
                repository.markHealthObservationSynced(entity, entity.localId);
            } catch (Exception e) {
                repository.markHealthObservationFailed(entity);
                throw e;
            }
        }
    }

    private void writeSharedRecord(String documentId, Map<String, Object> payload) throws Exception {
        Tasks.await(FirebaseFirestore.getInstance()
                .collection("shared_records")
                .document(documentId)
                .set(payload, SetOptions.merge()));
    }

    private void fanOutNotification(
            String recordId,
            String recordType,
            String actorUserId,
            String actorName,
            String title,
            String message
    ) throws Exception {
        for (var doc : Tasks.await(FirebaseFirestore.getInstance().collection("users").get())) {
            String recipientId = doc.getId();
            if (recipientId.equals(actorUserId)) {
                continue;
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("notificationId", recipientId + "_" + recordType + "_" + recordId);
            payload.put("recipientUserId", recipientId);
            payload.put("actorUserId", actorUserId);
            payload.put("actorName", actorName);
            payload.put("recordId", recordId);
            payload.put("recordType", recordType);
            payload.put("title", title);
            payload.put("message", message);
            payload.put("createdAt", System.currentTimeMillis());
            payload.put("isRead", false);
            payload.put("destination", recordType);
            Tasks.await(FirebaseFirestore.getInstance()
                    .collection("notifications")
                    .document(recipientId)
                    .collection("items")
                    .document(recipientId + "_" + recordType + "_" + recordId)
                    .set(payload, SetOptions.merge()));
        }
    }

    private Map<String, Object> buildSightingPayload(SightingEntity entity) {
        Map<String, Object> payload = basePayload(entity.localId, RecordType.SIGHTING, entity.rangerId, entity.authorName, entity.title, entity.notes, entity.timestamp, entity.lastModifiedAt);
        payload.put("latitude", entity.latitude);
        payload.put("longitude", entity.longitude);
        payload.put("radius", entity.radius);
        payload.put("audioPath", entity.audioPath);
        payload.put("imagePath", entity.imagePath);
        payload.put("videoPath", entity.videoPath);
        return payload;
    }

    private Map<String, Object> buildPatrolPayload(PatrolLogEntity entity) {
        Map<String, Object> payload = basePayload(entity.localId, RecordType.PATROL_LOG, entity.rangerId, entity.authorName, entity.title, entity.notes, entity.timestamp, entity.lastModifiedAt);
        payload.put("audioPath", entity.audioPath);
        payload.put("videoPath", entity.videoPath);
        return payload;
    }

    private Map<String, Object> buildHealthPayload(HealthObservationEntity entity) {
        Map<String, Object> payload = basePayload(entity.localId, RecordType.HEALTH_OBSERVATION, entity.authorId, entity.authorName, entity.title, entity.notes, entity.timestamp, entity.lastModifiedAt);
        payload.put("latitude", entity.latitude);
        payload.put("longitude", entity.longitude);
        return payload;
    }

    private Map<String, Object> basePayload(
            String recordId,
            String type,
            String authorId,
            String authorDisplayName,
            String title,
            String summary,
            long createdAt,
            long updatedAt
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("recordId", recordId);
        payload.put("type", type);
        payload.put("authorId", authorId);
        payload.put("authorDisplayName", authorDisplayName);
        payload.put("createdAt", createdAt);
        payload.put("updatedAt", updatedAt);
        payload.put("syncedAt", System.currentTimeMillis());
        payload.put("title", title);
        payload.put("summary", summary);
        return payload;
    }

    private String notificationMessage(String recordType, String actorName) {
        String safeName = actorName == null || actorName.isEmpty() ? "another ranger" : actorName;
        if (RecordType.SIGHTING.equals(recordType)) {
            return "New sighting added by " + safeName;
        }
        if (RecordType.PATROL_LOG.equals(recordType)) {
            return "New patrol log submitted by " + safeName;
        }
        return "New health observation recorded by " + safeName;
    }

    private int getBatteryPercent() {
        BatteryManager batteryManager = getApplicationContext().getSystemService(BatteryManager.class);
        if (batteryManager == null) {
            return 100;
        }
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }
}
