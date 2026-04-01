package com.sbs.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HealthObservationDao {

    @Query("SELECT * FROM health_observations WHERE rangerId = :rangerId ORDER BY timestamp DESC")
    LiveData<List<HealthObservationEntity>> observeAll(String rangerId);

    @Query("SELECT * FROM health_observations WHERE rangerId = :rangerId ORDER BY timestamp DESC")
    List<HealthObservationEntity> getAll(String rangerId);

    @Query("SELECT * FROM health_observations WHERE rangerId = :rangerId AND localId = :localId LIMIT 1")
    HealthObservationEntity getById(String rangerId, String localId);

    @Query("SELECT * FROM health_observations WHERE rangerId = :rangerId AND syncStatus IN (:statuses) ORDER BY timestamp ASC LIMIT :limit")
    List<HealthObservationEntity> getPending(String rangerId, String[] statuses, int limit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(HealthObservationEntity entity);

    @Query("DELETE FROM health_observations WHERE rangerId = :rangerId AND localId = :localId")
    void delete(String rangerId, String localId);
}
