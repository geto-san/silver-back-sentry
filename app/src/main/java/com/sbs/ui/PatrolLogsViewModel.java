package com.sbs.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.sbs.data.AppRepository;
import com.sbs.data.PatrolLogRecord;
import com.sbs.data.SyncScheduler;

import java.util.List;

public final class PatrolLogsViewModel extends AndroidViewModel {

    private final AppRepository repository;
    private final String currentUserId;

    private final LiveData<List<PatrolLogRecord>> patrolLogs;
    private final MutableLiveData<Boolean> isSaving = new MutableLiveData<>(false);

    public PatrolLogsViewModel(@NonNull Application application) {
        super(application);
        repository = AppRepository.getInstance(application);
        currentUserId = FirebaseAuth.getInstance().getUid();

        if (currentUserId != null) {
            patrolLogs = repository.observePatrolLogs(currentUserId);
        } else {
            patrolLogs = new MutableLiveData<>(java.util.Collections.emptyList());
        }
    }

    // ── Exposed LiveData ──────────────────────────────────────────────────────

    public LiveData<List<PatrolLogRecord>> getPatrolLogs() {
        return patrolLogs;
    }

    public LiveData<Boolean> getIsSaving() {
        return isSaving;
    }

    // ── Write operations ──────────────────────────────────────────────────────

    public void savePatrolLog(
            String localId,
            String title,
            String notes,
            long timestamp,
            String audioPath,
            String videoPath,
            AppRepository.RecordCallback<PatrolLogRecord> callback
    ) {
        if (currentUserId == null) return;
        isSaving.setValue(true);
        repository.savePatrolLog(
                currentUserId,
                localId,
                title,
                notes,
                timestamp,
                audioPath,
                videoPath,
                record -> {
                    isSaving.postValue(false);
                    SyncScheduler.enqueueSync(getApplication());
                    if (callback != null) callback.onLoaded(record);
                }
        );
    }

    public void deletePatrolLog(String localId) {
        if (currentUserId == null) return;
        repository.deletePatrolLog(currentUserId, localId);
    }

    public void loadPatrolLog(String localId, AppRepository.RecordCallback<PatrolLogRecord> callback) {
        if (currentUserId == null) return;
        repository.loadPatrolLog(currentUserId, localId, callback);
    }

    public String getCurrentUserId() {
        return currentUserId;
    }
}
