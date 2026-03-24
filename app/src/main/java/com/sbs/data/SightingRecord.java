package com.sbs.data;

public class SightingRecord {
    public final String localId;
    public final String firestoreId;
    public final String title;
    public final String notes;
    public final double lat;
    public final double lng;
    public final long timestamp;
    public final String authorId;
    public final String authorName;
    public final String syncStatus;
    public final long lastSyncAttempt;

    public SightingRecord(
            String localId,
            String firestoreId,
            String title,
            String notes,
            double lat,
            double lng,
            long timestamp,
            String authorId,
            String authorName,
            String syncStatus,
            long lastSyncAttempt
    ) {
        this.localId = localId;
        this.firestoreId = firestoreId;
        this.title = title;
        this.notes = notes;
        this.lat = lat;
        this.lng = lng;
        this.timestamp = timestamp;
        this.authorId = authorId;
        this.authorName = authorName;
        this.syncStatus = syncStatus;
        this.lastSyncAttempt = lastSyncAttempt;
    }
}
