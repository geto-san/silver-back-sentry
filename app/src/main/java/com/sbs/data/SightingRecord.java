package com.sbs.data;

public class SightingRecord {
    public final String localId;
    public final String firestoreId;
    public final String title;
    public final String notes;
    public final double lat;
    public final double lng;
    public final long timestamp;
    public final String rangerId;
    public final String authorName;
    public final String syncStatus;
    public final long lastSyncAttempt;
    
    // New fields for media and radius
    public final String audioPath;
    public final String imagePath;
    public final String videoPath;
    public final float radius;

    public SightingRecord(
            String localId,
            String firestoreId,
            String title,
            String notes,
            double lat,
            double lng,
            long timestamp,
            String rangerId,
            String authorName,
            String syncStatus,
            long lastSyncAttempt,
            String audioPath,
            String imagePath,
            String videoPath,
            float radius
    ) {
        this.localId = localId;
        this.firestoreId = firestoreId;
        this.title = title;
        this.notes = notes;
        this.lat = lat;
        this.lng = lng;
        this.timestamp = timestamp;
        this.rangerId = rangerId;
        this.authorName = authorName;
        this.syncStatus = syncStatus;
        this.lastSyncAttempt = lastSyncAttempt;
        this.audioPath = audioPath;
        this.imagePath = imagePath;
        this.videoPath = videoPath;
        this.radius = radius;
    }
}
