package com.sbs.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "app_notifications",
        foreignKeys = @ForeignKey(
                entity = RangerEntity.class,
                parentColumns = "rangerId",
                childColumns = "rangerId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index("rangerId"),
                @Index(value = {"rangerId", "recordId", "recordType"}, unique = true)
        }
)
public class AppNotificationEntity {

    @PrimaryKey
    @NonNull
    public String notificationId;

    @NonNull
    public String rangerId;

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
            @NonNull String rangerId,
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
        this.rangerId = rangerId;
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
