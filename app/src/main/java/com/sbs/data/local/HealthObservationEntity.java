package com.sbs.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "health_observations",
        foreignKeys = @ForeignKey(
                entity = RangerEntity.class,
                parentColumns = "rangerId",
                childColumns = "rangerId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index("rangerId"),
                @Index(value = "remoteId", unique = true),
                @Index("timestamp")
        }
)
public class HealthObservationEntity {

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
    public double latitude;
    public double longitude;
    public String syncStatus;
    public long lastSyncAttempt;
    public long lastModifiedAt;

    public HealthObservationEntity(
            @NonNull String localId,
            String remoteId,
            @NonNull String rangerId,
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
        this.rangerId = rangerId;
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
