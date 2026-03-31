package com.sbs.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "health_observations",
        indices = {
                @Index(value = "remoteId", unique = true),
                @Index("timestamp")
        }
)
public class HealthObservationEntity {

    @PrimaryKey
    @NonNull
    public String localId;

    public String remoteId;
    public String authorId;
    public String authorName;
    public String title;
    public String notes;
    public long timestamp;
    public double latitude;
    public double longitude;
    public String syncStatus;
    public long lastSyncAttempt;
    public long lastModifiedAt;

    public HealthObservationEntity(
            @NonNull String localId,
            String remoteId,
            String authorId,
            String authorName,
            String title,
            String notes,
            long timestamp,
            double latitude,
            double longitude,
            String syncStatus,
            long lastSyncAttempt,
            long lastModifiedAt
    ) {
        this.localId = localId;
        this.remoteId = remoteId;
        this.authorId = authorId;
        this.authorName = authorName;
        this.title = title;
        this.notes = notes;
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
        this.syncStatus = syncStatus;
        this.lastSyncAttempt = lastSyncAttempt;
        this.lastModifiedAt = lastModifiedAt;
    }
}
