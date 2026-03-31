package com.sbs.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.sbs.data.local.AppDatabase;
import com.sbs.data.local.AppNotificationEntity;
import com.sbs.data.local.HealthObservationEntity;
import com.sbs.data.local.PatrolLogEntity;
import com.sbs.data.local.RangerEntity;
import com.sbs.data.local.SightingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppRepository {

    public interface RecordCallback<T> {
        void onLoaded(T value);
    }

    private static volatile AppRepository instance;

    private final AppDatabase database;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AppRepository(Context context) {
        database = AppDatabase.getInstance(context);
        io.execute(() -> LegacyDataImporter.importIfNeeded(context.getApplicationContext(), database));
    }

    public static AppRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (AppRepository.class) {
                if (instance == null) {
                    instance = new AppRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public LiveData<List<SightingRecord>> observeSightings() {
        MediatorLiveData<List<SightingRecord>> liveData = new MediatorLiveData<>();
        liveData.addSource(database.sightingDao().observeAll(), entities -> liveData.setValue(mapSightings(entities)));
        return liveData;
    }

    public LiveData<List<PatrolLogRecord>> observePatrolLogs() {
        MediatorLiveData<List<PatrolLogRecord>> liveData = new MediatorLiveData<>();
        liveData.addSource(database.patrolLogDao().observeAll(), entities -> liveData.setValue(mapPatrolLogs(entities)));
        return liveData;
    }

    public LiveData<List<HealthObservationRecord>> observeHealthObservations() {
        MediatorLiveData<List<HealthObservationRecord>> liveData = new MediatorLiveData<>();
        liveData.addSource(database.healthObservationDao().observeAll(), entities -> liveData.setValue(mapHealthObservations(entities)));
        return liveData;
    }

    public LiveData<List<AppNotificationRecord>> observeNotifications(String recipientUserId) {
        MediatorLiveData<List<AppNotificationRecord>> liveData = new MediatorLiveData<>();
        liveData.addSource(database.appNotificationDao().observeByRecipient(recipientUserId), entities -> liveData.setValue(mapNotifications(entities)));
        return liveData;
    }

    public LiveData<Integer> observeUnreadNotificationCount(String recipientUserId) {
        return database.appNotificationDao().observeUnreadCount(recipientUserId);
    }

    public void loadSighting(String localId, RecordCallback<SightingRecord> callback) {
        io.execute(() -> post(callback, toRecord(database.sightingDao().getById(localId))));
    }

    public void loadPatrolLog(String localId, RecordCallback<PatrolLogRecord> callback) {
        io.execute(() -> post(callback, toRecord(database.patrolLogDao().getById(localId))));
    }

    public void loadHealthObservation(String localId, RecordCallback<HealthObservationRecord> callback) {
        io.execute(() -> post(callback, toRecord(database.healthObservationDao().getById(localId))));
    }

    public void saveSighting(
            String authorId,
            String localId,
            String title,
            String notes,
            double lat,
            double lng,
            long timestamp,
            float radius,
            String audioPath,
            String imagePath,
            String videoPath,
            RecordCallback<SightingRecord> callback
    ) {
        io.execute(() -> {
            long now = System.currentTimeMillis();
            String id = TextUtils.isEmpty(localId) ? UUID.randomUUID().toString() : localId;
            SightingEntity current = database.sightingDao().getById(id);
            upsertRanger(resolveCurrentUser());
            SightingEntity entity = new SightingEntity(
                    id,
                    current != null ? current.remoteId : null,
                    authorId,
                    resolveAuthorName(),
                    title,
                    notes,
                    lat,
                    lng,
                    timestamp,
                    radius,
                    coalesce(audioPath, current != null ? current.audioPath : null),
                    coalesce(imagePath, current != null ? current.imagePath : null),
                    coalesce(videoPath, current != null ? current.videoPath : null),
                    SyncState.PENDING,
                    current != null ? current.lastSyncAttempt : 0L,
                    now
            );
            database.sightingDao().upsert(entity);
            post(callback, toRecord(entity));
        });
    }

    public void savePatrolLog(
            String authorId,
            String localId,
            String title,
            String notes,
            long timestamp,
            String audioPath,
            String videoPath,
            RecordCallback<PatrolLogRecord> callback
    ) {
        io.execute(() -> {
            long now = System.currentTimeMillis();
            String id = TextUtils.isEmpty(localId) ? UUID.randomUUID().toString() : localId;
            PatrolLogEntity current = database.patrolLogDao().getById(id);
            upsertRanger(resolveCurrentUser());
            PatrolLogEntity entity = new PatrolLogEntity(
                    id,
                    current != null ? current.remoteId : null,
                    authorId,
                    resolveAuthorName(),
                    title,
                    notes,
                    timestamp,
                    coalesce(audioPath, current != null ? current.audioPath : null),
                    coalesce(videoPath, current != null ? current.videoPath : null),
                    SyncState.PENDING,
                    current != null ? current.lastSyncAttempt : 0L,
                    now
            );
            database.patrolLogDao().upsert(entity);
            post(callback, toRecord(entity));
        });
    }

    public void saveHealthObservation(
            String authorId,
            String localId,
            String title,
            String notes,
            long timestamp,
            double lat,
            double lng,
            RecordCallback<HealthObservationRecord> callback
    ) {
        io.execute(() -> {
            long now = System.currentTimeMillis();
            String id = TextUtils.isEmpty(localId) ? UUID.randomUUID().toString() : localId;
            HealthObservationEntity current = database.healthObservationDao().getById(id);
            upsertRanger(resolveCurrentUser());
            HealthObservationEntity entity = new HealthObservationEntity(
                    id,
                    current != null ? current.remoteId : null,
                    authorId,
                    resolveAuthorName(),
                    title,
                    notes,
                    timestamp,
                    lat,
                    lng,
                    SyncState.PENDING,
                    current != null ? current.lastSyncAttempt : 0L,
                    now
            );
            database.healthObservationDao().upsert(entity);
            post(callback, toRecord(entity));
        });
    }

    public void deleteSighting(String localId) {
        io.execute(() -> database.sightingDao().delete(localId));
    }

    public void deletePatrolLog(String localId) {
        io.execute(() -> database.patrolLogDao().delete(localId));
    }

    public void deleteHealthObservation(String localId) {
        io.execute(() -> database.healthObservationDao().delete(localId));
    }

    public void upsertRanger(FirebaseUser user) {
        if (user == null) {
            return;
        }
        long now = System.currentTimeMillis();
        database.rangerDao().upsert(new RangerEntity(
                user.getUid(),
                resolveDisplayName(user),
                user.getEmail(),
                now,
                now
        ));
    }

    public void upsertCurrentRanger() {
        io.execute(() -> upsertRanger(resolveCurrentUser()));
    }

    public List<SightingEntity> getPendingSightings(int limit) {
        return database.sightingDao().getPending(new String[]{SyncState.PENDING, SyncState.FAILED}, limit);
    }

    public List<PatrolLogEntity> getPendingPatrolLogs(int limit) {
        return database.patrolLogDao().getPending(new String[]{SyncState.PENDING, SyncState.FAILED}, limit);
    }

    public List<HealthObservationEntity> getPendingHealthObservations(int limit) {
        return database.healthObservationDao().getPending(new String[]{SyncState.PENDING, SyncState.FAILED}, limit);
    }

    public void markSightingSyncing(SightingEntity entity) {
        entity.syncStatus = SyncState.SYNCING;
        database.sightingDao().upsert(entity);
    }

    public void markSightingSynced(SightingEntity entity, String remoteId) {
        entity.remoteId = remoteId;
        entity.syncStatus = SyncState.SYNCED;
        entity.lastSyncAttempt = System.currentTimeMillis();
        database.sightingDao().upsert(entity);
    }

    public void markSightingFailed(SightingEntity entity) {
        entity.syncStatus = SyncState.FAILED;
        entity.lastSyncAttempt = System.currentTimeMillis();
        database.sightingDao().upsert(entity);
    }

    public void markPatrolLogSyncing(PatrolLogEntity entity) {
        entity.syncStatus = SyncState.SYNCING;
        database.patrolLogDao().upsert(entity);
    }

    public void markPatrolLogSynced(PatrolLogEntity entity, String remoteId) {
        entity.remoteId = remoteId;
        entity.syncStatus = SyncState.SYNCED;
        entity.lastSyncAttempt = System.currentTimeMillis();
        database.patrolLogDao().upsert(entity);
    }

    public void markPatrolLogFailed(PatrolLogEntity entity) {
        entity.syncStatus = SyncState.FAILED;
        entity.lastSyncAttempt = System.currentTimeMillis();
        database.patrolLogDao().upsert(entity);
    }

    public void markHealthObservationSyncing(HealthObservationEntity entity) {
        entity.syncStatus = SyncState.SYNCING;
        database.healthObservationDao().upsert(entity);
    }

    public void markHealthObservationSynced(HealthObservationEntity entity, String remoteId) {
        entity.remoteId = remoteId;
        entity.syncStatus = SyncState.SYNCED;
        entity.lastSyncAttempt = System.currentTimeMillis();
        database.healthObservationDao().upsert(entity);
    }

    public void markHealthObservationFailed(HealthObservationEntity entity) {
        entity.syncStatus = SyncState.FAILED;
        entity.lastSyncAttempt = System.currentTimeMillis();
        database.healthObservationDao().upsert(entity);
    }

    public void mergeRemoteSightings(List<SightingEntity> entities) {
        for (SightingEntity entity : entities) {
            SightingEntity local = database.sightingDao().getById(entity.localId);
            if (shouldReplace(local, entity.lastModifiedAt)) {
                database.sightingDao().upsert(entity);
            }
        }
    }

    public void mergeRemotePatrolLogs(List<PatrolLogEntity> entities) {
        for (PatrolLogEntity entity : entities) {
            PatrolLogEntity local = database.patrolLogDao().getById(entity.localId);
            if (shouldReplace(local, entity.lastModifiedAt)) {
                database.patrolLogDao().upsert(entity);
            }
        }
    }

    public void mergeRemoteHealthObservations(List<HealthObservationEntity> entities) {
        for (HealthObservationEntity entity : entities) {
            HealthObservationEntity local = database.healthObservationDao().getById(entity.localId);
            if (shouldReplace(local, entity.lastModifiedAt)) {
                database.healthObservationDao().upsert(entity);
            }
        }
    }

    public void mergeNotifications(List<AppNotificationEntity> entities) {
        database.appNotificationDao().upsertAll(entities);
    }

    public void markNotificationRead(String recipientUserId, String notificationId) {
        io.execute(() -> database.appNotificationDao().markRead(recipientUserId, notificationId));
    }

    public void markAllNotificationsRead(String recipientUserId) {
        io.execute(() -> database.appNotificationDao().markAllRead(recipientUserId));
    }

    public List<AppNotificationEntity> getPendingSystemNotifications(String recipientUserId) {
        return database.appNotificationDao().getPendingSystemNotifications(recipientUserId);
    }

    public void markNotificationSystemNotified(String notificationId) {
        database.appNotificationDao().markSystemNotified(notificationId);
    }

    public void runOnIo(Runnable action) {
        io.execute(action);
    }

    private <T> void post(RecordCallback<T> callback, T value) {
        if (callback != null) {
            mainHandler.post(() -> callback.onLoaded(value));
        }
    }

    private static boolean shouldReplace(Object local, long incomingLastModifiedAt) {
        if (local == null) {
            return true;
        }
        if (local instanceof SightingEntity) {
            SightingEntity entity = (SightingEntity) local;
            return SyncState.SYNCED.equals(entity.syncStatus) || entity.lastModifiedAt <= incomingLastModifiedAt;
        }
        if (local instanceof PatrolLogEntity) {
            PatrolLogEntity entity = (PatrolLogEntity) local;
            return SyncState.SYNCED.equals(entity.syncStatus) || entity.lastModifiedAt <= incomingLastModifiedAt;
        }
        if (local instanceof HealthObservationEntity) {
            HealthObservationEntity entity = (HealthObservationEntity) local;
            return SyncState.SYNCED.equals(entity.syncStatus) || entity.lastModifiedAt <= incomingLastModifiedAt;
        }
        return true;
    }

    private static FirebaseUser resolveCurrentUser() {
        return FirebaseAuth.getInstance().getCurrentUser();
    }

    private static String resolveAuthorName() {
        FirebaseUser user = resolveCurrentUser();
        return user == null ? null : resolveDisplayName(user);
    }

    private static String resolveDisplayName(FirebaseUser user) {
        if (!TextUtils.isEmpty(user.getDisplayName())) {
            return user.getDisplayName();
        }
        String email = user.getEmail();
        if (TextUtils.isEmpty(email)) {
            return "Ranger";
        }
        int atIndex = email.indexOf('@');
        return atIndex > 0 ? email.substring(0, atIndex) : email;
    }

    private static String coalesce(String preferred, String fallback) {
        return !TextUtils.isEmpty(preferred) ? preferred : fallback;
    }

    private static List<SightingRecord> mapSightings(List<SightingEntity> entities) {
        List<SightingRecord> records = new ArrayList<>(entities.size());
        for (SightingEntity entity : entities) {
            records.add(toRecord(entity));
        }
        return records;
    }

    private static List<PatrolLogRecord> mapPatrolLogs(List<PatrolLogEntity> entities) {
        List<PatrolLogRecord> records = new ArrayList<>(entities.size());
        for (PatrolLogEntity entity : entities) {
            records.add(toRecord(entity));
        }
        return records;
    }

    private static List<HealthObservationRecord> mapHealthObservations(List<HealthObservationEntity> entities) {
        List<HealthObservationRecord> records = new ArrayList<>(entities.size());
        for (HealthObservationEntity entity : entities) {
            records.add(toRecord(entity));
        }
        return records;
    }

    private static List<AppNotificationRecord> mapNotifications(List<AppNotificationEntity> entities) {
        List<AppNotificationRecord> records = new ArrayList<>(entities.size());
        for (AppNotificationEntity entity : entities) {
            records.add(toRecord(entity));
        }
        return records;
    }

    private static SightingRecord toRecord(SightingEntity entity) {
        if (entity == null) {
            return null;
        }
        return new SightingRecord(
                entity.localId,
                entity.remoteId,
                entity.title,
                entity.notes,
                entity.latitude,
                entity.longitude,
                entity.timestamp,
                entity.rangerId,
                entity.authorName,
                entity.syncStatus,
                entity.lastSyncAttempt,
                entity.audioPath,
                entity.imagePath,
                entity.videoPath,
                entity.radius
        );
    }

    private static PatrolLogRecord toRecord(PatrolLogEntity entity) {
        if (entity == null) {
            return null;
        }
        return new PatrolLogRecord(
                entity.localId,
                entity.remoteId,
                entity.title,
                entity.notes,
                entity.timestamp,
                entity.rangerId,
                entity.authorName,
                entity.syncStatus,
                entity.audioPath,
                entity.videoPath
        );
    }

    private static HealthObservationRecord toRecord(HealthObservationEntity entity) {
        if (entity == null) {
            return null;
        }
        return new HealthObservationRecord(
                entity.localId,
                entity.remoteId,
                entity.title,
                entity.notes,
                entity.timestamp,
                entity.authorId,
                entity.authorName,
                entity.syncStatus,
                entity.lastSyncAttempt,
                entity.latitude,
                entity.longitude
        );
    }

    private static AppNotificationRecord toRecord(AppNotificationEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AppNotificationRecord(
                entity.notificationId,
                entity.recipientUserId,
                entity.actorUserId,
                entity.actorName,
                entity.recordId,
                entity.recordType,
                entity.title,
                entity.message,
                entity.createdAt,
                entity.isRead,
                entity.destination
        );
    }
}
