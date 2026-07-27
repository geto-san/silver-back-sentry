package com.sbs.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "rangers")
public class RangerEntity {

    @PrimaryKey
    @NonNull
    public String rangerId;

    public String fullName;
    public String email;
    public long createdAt;
    public long updatedAt;

    public RangerEntity(@NonNull String rangerId, String fullName, String email, long createdAt, long updatedAt) {
        this.rangerId = rangerId;
        this.fullName = fullName;
        this.email = email;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
