package com.sbs.data;

public class AppNotificationRecord {
    public final String notificationId;
    public final String recipientUserId;
    public final String actorUserId;
    public final String actorName;
    public final String recordId;
    public final String recordType;
    public final String title;
    public final String message;
    public final long createdAt;
    public final boolean isRead;
    public final String destination;

    public AppNotificationRecord(
            String notificationId,
            String recipientUserId,
            String actorUserId,
            String actorName,
            String recordId,
            String recordType,
            String title,
            String message,
            long createdAt,
            boolean isRead,
            String destination
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
    }
}
