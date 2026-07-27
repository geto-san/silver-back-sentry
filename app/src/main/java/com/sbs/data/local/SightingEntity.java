package com.sbs.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "sightings",
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
public class SightingEntity {

    @PrimaryKey
    @NonNull
    public String localId;

    public String remoteId;

    @NonNull
    public String rangerId;

    public String authorName;
    public String title;
    public String notes;
    public double latitude;
    public double longitude;
    public long timestamp;
    public float radius;
    public String audioPath;
    public String imagePath;
    public String videoPath;
    public String syncStatus;
    public long lastSyncAttempt;
    public long lastModifiedAt;

    public SightingEntity(
            @NonNull String localId,
            String remoteId,
            @NonNull String rangerId,
            String authorName,
            String title,
            String notes,
            double latitude,
            double longitude,
            long timestamp,
            float radius,
            String audioPath,
            String imagePath,
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
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.radius = radius;
        this.audioPath = audioPath;
        this.imagePath = imagePath;
        this.videoPath = videoPath;
        this.syncStatus = syncStatus;
        this.lastSyncAttempt = lastSyncAttempt;
        this.lastModifiedAt = lastModifiedAt;
    }
}
