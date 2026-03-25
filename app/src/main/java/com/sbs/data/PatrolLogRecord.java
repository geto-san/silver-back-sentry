package com.sbs.data;

public class PatrolLogRecord {
    public final String localId;
    public final String firestoreId;
    public final String title;
    public final String notes;
    public final long timestamp;
    public final String authorId;
    public final String authorName;
    public final String syncStatus;
    
    public final String audioPath;
    public final String videoPath;

    public PatrolLogRecord(
            String localId,
            String firestoreId,
            String title,
            String notes,
            long timestamp,
            String authorId,
            String authorName,
            String syncStatus,
            String audioPath,
            String videoPath
    ) {
        this.localId = localId;
        this.firestoreId = firestoreId;
        this.title = title;
        this.notes = notes;
        this.timestamp = timestamp;
        this.authorId = authorId;
        this.authorName = authorName;
        this.syncStatus = syncStatus;
        this.audioPath = audioPath;
        this.videoPath = videoPath;
    }
}
