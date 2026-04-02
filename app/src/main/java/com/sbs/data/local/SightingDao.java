package com.sbs.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface SightingDao {

    @Query("SELECT * FROM sightings WHERE rangerId = :rangerId ORDER BY timestamp DESC")
    LiveData<List<SightingEntity>> observeAll(String rangerId);

    @Query("SELECT * FROM sightings WHERE rangerId = :rangerId ORDER BY timestamp DESC")
    List<SightingEntity> getAll(String rangerId);

    @Query("SELECT * FROM sightings WHERE rangerId = :rangerId AND localId = :localId LIMIT 1")
    SightingEntity getById(String rangerId, String localId);

    @Query("SELECT * FROM sightings WHERE rangerId = :rangerId AND syncStatus IN (:statuses) ORDER BY timestamp ASC LIMIT :limit")
    List<SightingEntity> getPending(String rangerId, String[] statuses, int limit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SightingEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<SightingEntity> entities);

    @Update
    void update(SightingEntity entity);

    @Query("DELETE FROM sightings WHERE rangerId = :rangerId AND localId = :localId")
    void delete(String rangerId, String localId);
}
