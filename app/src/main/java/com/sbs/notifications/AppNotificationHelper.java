package com.sbs.notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.sbs.R;
import com.sbs.data.AppNotificationRecord;
import com.sbs.ui.DashboardActivity;

public final class AppNotificationHelper {

    public static final String CHANNEL_ID = "sbs_shared_updates";

    private AppNotificationHelper() {
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) {
                return;
            }
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Shared ranger updates",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Updates from other rangers");
            manager.createNotificationChannel(channel);
        }
    }

    public static void showRecordNotification(Context context, AppNotificationRecord notification) {
        ensureChannel(context);
        Intent intent = new Intent(context, DashboardActivity.class);
        intent.putExtra("notification_id", notification.notificationId);
        intent.putExtra("record_id", notification.recordId);
        intent.putExtra("record_type", notification.recordType);
        intent.putExtra("destination", notification.destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notification.notificationId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(notification.title)
                .setContentText(notification.message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notification.message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(notification.notificationId.hashCode(), builder.build());
        }
    }
}
