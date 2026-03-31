package com.sbs.notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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

public class SbsFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "SbsFCM";
    private static final String CHANNEL_ID = "sbs_alerts";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "FCM token: " + token);
        FcmTokenManager.registerTokenIfPossible(this, token);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title = "Silver Back Sentry";
        String body = "You have a new alert.";
        String type = "general";
        String recordId = null;

        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
            }
            if (remoteMessage.getNotification().getBody() != null) {
                body = remoteMessage.getNotification().getBody();
            }
        }

        if (!remoteMessage.getData().isEmpty()) {
            if (remoteMessage.getData().get("title") != null) {
                title = remoteMessage.getData().get("title");
            }
            if (remoteMessage.getData().get("body") != null) {
                body = remoteMessage.getData().get("body");
            }
            if (remoteMessage.getData().get("type") != null) {
                type = remoteMessage.getData().get("type");
            }
            recordId = remoteMessage.getData().get("recordId");
        }

        AppNotificationHelper.ensureChannel(this);

        Intent intent = new Intent(this, com.sbs.ui.DashboardActivity.class);
        intent.putExtra("record_type", type);
        intent.putExtra("record_id", recordId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, type.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, AppNotificationHelper.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this)
                    .notify((int) System.currentTimeMillis(), builder.build());
        }
    }
}
