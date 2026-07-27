package com.sbs.notifications;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.sbs.R;
import com.sbs.data.SyncScheduler;
import com.sbs.ui.DashboardActivity;

import java.util.Map;

/**
 * SbsFirebaseMessagingService — Feature 3: FCM Integration
 *
 * Receives Firebase Cloud Messaging push messages even when the app is
 * completely closed (process not running).  The Android OS wakes this
 * service, calls onMessageReceived(), and shuts it down after the method
 * returns — so no long-running work may be done here directly.
 *
 * Two message types are handled:
 *
 *   1. Sync trigger  (data key "type" = "sync")
 *      Mirrors NowInAndroid's SyncNotificationsService exactly.
 *      The server sends this message when new shared records are available.
 *      We immediately enqueue a WorkManager sync job — WorkManager handles
 *      constraints (network available, battery OK) and scheduling.
 *      No notification is shown; this is a silent background sync trigger.
 *
 *   2. Conservation alert  (data key "type" = "alert" or any other type,
 *      or a Firebase notification payload)
 *      Displays a high-priority system tray notification.
 *      Tapping the notification opens DashboardActivity and, if a record
 *      ID was included, navigates directly to that record.
 *
 * Architecture note:
 *   Heavy work (Firestore reads/writes, Room queries) must NOT be done
 *   here.  Delegate all such work to WorkManager.  This service is only
 *   a lightweight dispatcher:
 *       FCM push → SbsFirebaseMessagingService.onMessageReceived()
 *           → SyncScheduler.enqueueSync()  (WorkManager)
 *           OR
 *           → AppNotificationHelper.showRecordNotification()  (system tray)
 *
 * Token refresh:
 *   onNewToken() is called by FCM whenever the device registration token
 *   changes (first install, app data cleared, token rotated by Google).
 *   We store the new token in Firestore so the server can target this
 *   device for future pushes.
 */
public class SbsFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "SbsFCM";

    // ── Message type constants ────────────────────────────────────────────────

    /** Data key that carries the message category. */
    private static final String KEY_TYPE      = "type";
    /** Data key for the human-readable alert title. */
    private static final String KEY_TITLE     = "title";
    /** Data key for the alert body text. */
    private static final String KEY_BODY      = "body";
    /** Data key for the record ID to deep-link into. */
    private static final String KEY_RECORD_ID = "recordId";
    /** Data key for the record type (SIGHTING / PATROL_LOG / HEALTH_OBSERVATION). */
    private static final String KEY_RECORD_TYPE = "recordType";

    /**
     * Message type value that triggers a background data sync.
     * Mirrors NowInAndroid's SYNC_TOPIC_SENDER constant.
     * When the server has new shared records to distribute it sends a data
     * message with type = "sync" to the /topics/sync FCM topic.
     */
    private static final String TYPE_SYNC = "sync";

    // ── FCM callbacks ─────────────────────────────────────────────────────────

    /**
     * Called when a message is received from FCM.
     *
     * Execution context: background thread managed by FCM.
     * Time limit:        ~20 seconds before the OS kills the process.
     * Constraints:       No long-running I/O; no direct Firestore calls.
     *                    Delegate heavy work to WorkManager.
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "FCM message received from: " + remoteMessage.getFrom());

        // ── Resolve message content ───────────────────────────────────────────
        // Prefer the data payload over the notification payload so that
        // messages work correctly when the app is in the foreground AND
        // background (notification-only messages are handled by the system
        // when the app is backgrounded and never reach this method).

        Map<String, String> data = remoteMessage.getData();

        String type       = data.getOrDefault(KEY_TYPE, "alert");
        String title      = data.getOrDefault(KEY_TITLE, null);
        String body       = data.getOrDefault(KEY_BODY,  null);
        String recordId   = data.getOrDefault(KEY_RECORD_ID, null);
        String recordType = data.getOrDefault(KEY_RECORD_TYPE, null);

        // Fall back to the notification payload when data fields are absent
        if (title == null && remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
        }
        if (body == null && remoteMessage.getNotification() != null) {
            body = remoteMessage.getNotification().getBody();
        }

        // Apply safe defaults
        if (title == null || title.isEmpty()) title = getString(R.string.app_name);
        if (body  == null || body.isEmpty())  body  = "You have a new update.";

        // ── Dispatch ──────────────────────────────────────────────────────────

        if (TYPE_SYNC.equals(type)) {
            handleSyncMessage();
        } else {
            handleAlertMessage(title, body, recordId, recordType);
        }
    }

    /**
     * Called when the FCM registration token for this device is refreshed.
     *
     * This happens on:
     *   • First app install / first FCM registration
     *   • App data cleared by the user
     *   • Token rotated by Google Play Services
     *
     * We store the new token in Firestore so the server can target this
     * device for direct (non-topic) pushes such as personal notifications.
     */
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "FCM token refreshed: " + token);
        // Persist the new token in Firestore under users/{uid}/fcmTokens/{token}
        FcmTokenManager.registerTokenIfPossible(this, token);
    }

    // ── Private dispatch methods ──────────────────────────────────────────────

    /**
     * Handles a "type=sync" data message.
     *
     * Mirrors NowInAndroid's SyncNotificationsService.onMessageReceived():
     *   if (SYNC_TOPIC_SENDER.equals(message.getFrom())) {
     *       syncManager.requestSync();
     *   }
     *
     * We enqueue a WorkManager one-time work request via SyncScheduler.
     * WorkManager will honour its own constraints (CONNECTED network,
     * battery not low) before actually running the sync workers.
     * If a sync is already queued or running, ExistingWorkPolicy.KEEP
     * ensures no duplicate jobs are created.
     */
    private void handleSyncMessage() {
        Log.d(TAG, "Sync trigger received — enqueueing WorkManager sync");
        SyncScheduler.enqueueSync(getApplicationContext());
    }

    /**
     * Handles an alert / notification message.
     *
     * Builds and posts a high-priority system tray notification.
     * Tapping the notification launches DashboardActivity.  If a
     * recordId and recordType were provided, they are passed as extras
     * so DashboardActivity can navigate directly to the relevant record.
     *
     * Permission check: POST_NOTIFICATIONS is required on Android 13+
     * (API 33).  On older versions the notification is always shown.
     */
    private void handleAlertMessage(String title,
                                    String body,
                                    String recordId,
                                    String recordType) {

        // ── Ensure notification channel exists (Android 8+) ──────────────────
        AppNotificationHelper.ensureChannel(this);

        // ── Build deep-link intent ────────────────────────────────────────────
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                      | Intent.FLAG_ACTIVITY_SINGLE_TOP
                      | Intent.FLAG_ACTIVITY_NEW_TASK);

        if (recordId   != null) intent.putExtra("record_id",   recordId);
        if (recordType != null) intent.putExtra("record_type", recordType);

        // Use recordType hashcode (or a fixed code) as the request code so
        // that multiple notifications of the same type share their PendingIntent
        // and update rather than stack.
        int requestCode = recordType != null ? recordType.hashCode() : 0;

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // ── Build notification ────────────────────────────────────────────────
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, AppNotificationHelper.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);

        // ── Post notification (check POST_NOTIFICATIONS on API 33+) ──────────
        boolean hasPermission =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(
                        this, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;

        if (hasPermission) {
            // Use current time as notification ID so each message creates a
            // new entry in the notification shade (not replacing a previous one).
            NotificationManagerCompat.from(this)
                    .notify((int) System.currentTimeMillis(), builder.build());
            Log.d(TAG, "Alert notification posted: " + title);
        } else {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted — notification suppressed");
        }
    }
}
