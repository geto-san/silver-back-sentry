package com.sbs.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "patrol_logs",
        foreignKeys = @ForeignKey(
                entity = RangerEntity.class,
                parentColumns = "rangerId",
                childColumns = "rangerId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index("rangerId"),
                @Index(value = "remoteId", unique = true),
                @Index(value = {"rangerId", "timestamp"})
        }
)
public class PatrolLogEntity {

    @PrimaryKey
    @NonNull
    public String localId;

    public String remoteId;

    @NonNull
    public String rangerId;

    public String authorName;
    public String title;
    public String notes;
    public long timestamp;
    public String audioPath;
    public String videoPath;
    public String syncStatus;
    public long lastSyncAttempt;
    public long lastModifiedAt;

    public PatrolLogEntity(
            @NonNull String localId,
            String remoteId,
            @NonNull String rangerId,
            String authorName,
            String title,
            String notes,
            long timestamp,
            String audioPath,
            String videoPath,
            String syncStatus,
            long lastSyncAttempt,
            long lastModifiedAt
    ) {
        this.localId = localId;
        this.remoteId = remoteId;
        this.rangerId = rangerId;
        this.authorName = authorName;
        this.title = title;
        this.notes = notes;
        this.timestamp = timestamp;
        this.audioPath = audioPath;
        this.videoPath = videoPath;
        this.syncStatus = syncStatus;
        this.lastSyncAttempt = lastSyncAttempt;
        this.lastModifiedAt = lastModifiedAt;
    }
}
