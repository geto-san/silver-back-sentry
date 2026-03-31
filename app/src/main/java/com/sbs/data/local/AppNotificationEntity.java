package com.sbs.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "app_notifications",
        indices = {
                @Index("recipientUserId"),
                @Index(value = {"recipientUserId", "recordId", "recordType"}, unique = true)
        }
)
public class AppNotificationEntity {

    @PrimaryKey
    @NonNull
    public String notificationId;

    public String recipientUserId;
    public String actorUserId;
    public String actorName;
    public String recordId;
    public String recordType;
    public String title;
    public String message;
    public long createdAt;
    public boolean isRead;
    public String destination;
    public boolean systemNotified;

    public AppNotificationEntity(
            @NonNull String notificationId,
            String recipientUserId,
            String actorUserId,
            String actorName,
            String recordId,
            String recordType,
            String title,
            String message,
            long createdAt,
            boolean isRead,
            String destination,
            boolean systemNotified
    ) {
        this.notificationId = notificationId;
        this.recipientUserId = recipientUserId;
        this.actorUserId = actorUserId;
        this.actorName = actorName;
        this.recordId = recordId;
        this.recordType = recordType;
        this.title = title;
        this.message = message;
        this.createdAt = createdAt;
        this.isRead = isRead;
        this.destination = destination;
        this.systemNotified = systemNotified;
    }
}
