package com.sbs.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.sbs.data.AppNotificationRecord;
import com.sbs.data.AppRepository;

import java.util.Collections;
import java.util.List;

/**
 * ViewModel for NotificationsActivity.
 *
 * Follows the single-source-of-truth principle from the architecture plan:
 * the UI only ever observes LiveData exposed here — it never touches
 * AppRepository or the DAO directly.
 *
 * Lifecycle safety: because this is an AndroidViewModel, it survives
 * configuration changes (screen rotation). The LiveData stream from Room
 * automatically re-emits to the new Activity observer without re-querying.
 */
public final class NotificationsViewModel extends AndroidViewModel {

    private final AppRepository repository;
    private final String currentUserId;

    private final LiveData<List<AppNotificationRecord>> notifications;
    private final LiveData<Integer> unreadCount;

    // Tracks whether a "mark all read" operation is in progress
    private final MutableLiveData<Boolean> isMarkingRead = new MutableLiveData<>(false);

    public NotificationsViewModel(@NonNull Application application) {
        super(application);
        repository     = AppRepository.getInstance(application);
        currentUserId  = FirebaseAuth.getInstance().getUid();

        if (currentUserId != null) {
            notifications = repository.observeNotifications(currentUserId);
            unreadCount   = repository.observeUnreadNotificationCount(currentUserId);
        } else {
            notifications = new MutableLiveData<>(Collections.emptyList());
            unreadCount   = new MutableLiveData<>(0);
        }
    }

    // ── Exposed state ─────────────────────────────────────────────────────────

    /** Full list of notifications for the current ranger, newest first. */
    public LiveData<List<AppNotificationRecord>> getNotifications() {
        return notifications;
    }

    /** Count of unread notifications — useful for a badge on the nav item. */
    public LiveData<Integer> getUnreadCount() {
        return unreadCount;
    }

    /** True while a mark-all-read operation is being dispatched. */
    public LiveData<Boolean> getIsMarkingRead() {
        return isMarkingRead;
    }

    public String getCurrentUserId() {
        return currentUserId;
    }

    // ── Write operations ──────────────────────────────────────────────────────

    /**
     * Mark a single notification as read.
     * Room will emit an updated list automatically — no manual UI refresh needed.
     */
    public void markRead(String notificationId) {
        if (currentUserId == null || notificationId == null) return;
        repository.markNotificationRead(currentUserId, notificationId);
    }

    /**
     * Mark every notification for the current ranger as read.
     * Sets isMarkingRead = true while the operation is dispatched so the UI
     * can show a loading state if desired.
     */
    public void markAllRead() {
        if (currentUserId == null) return;
        isMarkingRead.setValue(true);
        repository.markAllNotificationsRead(currentUserId);
        // markAllNotificationsRead is fire-and-forget on the IO executor;
        // Room LiveData will emit the update, at which point the UI refreshes.
        // We reset the flag immediately since the dispatch itself is instant.
        isMarkingRead.setValue(false);
    }
}
