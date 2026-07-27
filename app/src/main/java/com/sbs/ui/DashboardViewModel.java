package com.sbs.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.firebase.auth.FirebaseAuth;
import com.sbs.data.AppRepository;
import com.sbs.data.SightingRecord;

import java.util.Collections;
import java.util.List;

/**
 * DashboardViewModel
 *
 * Follows the architecture devised from NowInAndroid:
 *  - Room is the single source of truth; all reads come from LiveData-backed DAO queries.
 *  - The ViewModel never touches a network response directly.
 *  - WorkManager sync status is surfaced as a LiveData<Boolean> so the UI can show
 *    a progress indicator without polling.
 *
 * Lifecycle note: because this extends AndroidViewModel (not plain ViewModel) it can
 * safely hold an Application reference without leaking an Activity context.
 */
public final class DashboardViewModel extends AndroidViewModel {

    private static final String SYNC_WORK_NAME = "one_time_sync";

    private final AppRepository repository;
    private final String currentUserId;

    // ── Sightings (for map markers) ───────────────────────────────────────────
    private final LiveData<List<SightingRecord>> sightings;

    // ── Unread notification badge count ──────────────────────────────────────
    private final LiveData<Integer> unreadNotificationCount;

    // ── WorkManager sync status ───────────────────────────────────────────────
    // Emits true while any sync/upload job is RUNNING, false otherwise.
    // Mirrors NowInAndroid's WorkManagerSyncManager.isSyncing pattern.
    private final LiveData<Boolean> isSyncing;

    // ── Transient UI state (not persisted) ────────────────────────────────────
    private final MutableLiveData<String> snackbarMessage = new MutableLiveData<>();

    public DashboardViewModel(@NonNull Application application) {
        super(application);
        repository = AppRepository.getInstance(application);
        currentUserId = FirebaseAuth.getInstance().getUid();

        if (currentUserId != null) {
            sightings              = repository.observeSightings(currentUserId);
            unreadNotificationCount = repository.observeUnreadNotificationCount(currentUserId);
        } else {
            sightings               = new MutableLiveData<>(Collections.emptyList());
            unreadNotificationCount = new MutableLiveData<>(0);
        }

        // Observe all work tagged with the sync work name and map RUNNING state → boolean
        isSyncing = Transformations.map(
                WorkManager.getInstance(application)
                        .getWorkInfosForUniqueWorkLiveData(SYNC_WORK_NAME),
                workInfos -> {
                    if (workInfos == null) return false;
                    for (WorkInfo info : workInfos) {
                        if (info.getState() == WorkInfo.State.RUNNING) return true;
                    }
                    return false;
                }
        );
    }

    // ── Public accessors ──────────────────────────────────────────────────────

    /**
     * Live stream of sightings for the current ranger.
     * Observed by DashboardActivity to plot map markers.
     * Room emits a new list automatically after any write — the map re-renders
     * immediately without any manual refresh call.
     */
    public LiveData<List<SightingRecord>> getSightings() {
        return sightings;
    }

    /**
     * Unread notification count for the badge on the notification icon.
     */
    public LiveData<Integer> getUnreadNotificationCount() {
        return unreadNotificationCount;
    }

    /**
     * True while a WorkManager sync/upload job is actively running.
     * The UI can show a small progress bar or spinner when this is true.
     */
    public LiveData<Boolean> getIsSyncing() {
        return isSyncing;
    }

    /**
     * One-shot snackbar messages surfaced from background operations.
     */
    public LiveData<String> getSnackbarMessage() {
        return snackbarMessage;
    }

    public String getCurrentUserId() {
        return currentUserId;
    }

    // ── Write operations ──────────────────────────────────────────────────────

    /**
     * Marks a single notification as read.
     * The change is written to Room; the unread count LiveData updates automatically.
     */
    public void markNotificationRead(String notificationId) {
        if (currentUserId != null) {
            repository.markNotificationRead(currentUserId, notificationId);
        }
    }

    /**
     * Marks all notifications for the current ranger as read.
     */
    public void markAllNotificationsRead() {
        if (currentUserId != null) {
            repository.markAllNotificationsRead(currentUserId);
        }
    }

    /**
     * Triggers a message to be shown once in the UI (e.g. after an action).
     */
    public void showMessage(String message) {
        snackbarMessage.setValue(message);
    }

    /**
     * Clears the snackbar message after it has been shown,
     * preventing it from re-appearing on configuration change.
     */
    public void onSnackbarShown() {
        snackbarMessage.setValue(null);
    }
}
