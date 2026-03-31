package com.sbs.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HealthObservationDao {

    @Query("SELECT * FROM health_observations ORDER BY timestamp DESC")
    LiveData<List<HealthObservationEntity>> observeAll();

    @Query("SELECT * FROM health_observations ORDER BY timestamp DESC")
    List<HealthObservationEntity> getAll();

    @Query("SELECT * FROM health_observations WHERE localId = :localId LIMIT 1")
    HealthObservationEntity getById(String localId);

    @Query("SELECT * FROM health_observations WHERE syncStatus IN (:statuses) ORDER BY timestamp ASC LIMIT :limit")
    List<HealthObservationEntity> getPending(String[] statuses, int limit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(HealthObservationEntity entity);

    @Query("DELETE FROM health_observations WHERE localId = :localId")
    void delete(String localId);
}
