package com.sbs.data;

public class HealthObservationRecord {
    public final String localId;
    public final String remoteId;
    public final String title;
    public final String notes;
    public final long timestamp;
    public final String authorId;
    public final String authorName;
    public final String syncStatus;
    public final long lastSyncAttempt;
    public final double lat;
    public final double lng;

    public HealthObservationRecord(
            String localId,
            String remoteId,
            String title,
            String notes,
            long timestamp,
            String authorId,
            String authorName,
            String syncStatus,
            long lastSyncAttempt,
            double lat,
            double lng
    ) {
        this.localId = localId;
        this.remoteId = remoteId;
        this.title = title;
        this.notes = notes;
        this.timestamp = timestamp;
        this.authorId = authorId;
        this.authorName = authorName;
        this.syncStatus = syncStatus;
        this.lastSyncAttempt = lastSyncAttempt;
        this.lat = lat;
        this.lng = lng;
    }
}
