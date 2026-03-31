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

    @Query("SELECT * FROM sightings ORDER BY timestamp DESC")
    LiveData<List<SightingEntity>> observeAll();

    @Query("SELECT * FROM sightings ORDER BY timestamp DESC")
    List<SightingEntity> getAll();

    @Query("SELECT * FROM sightings WHERE localId = :localId LIMIT 1")
    SightingEntity getById(String localId);

    @Query("SELECT * FROM sightings WHERE syncStatus IN (:statuses) ORDER BY timestamp ASC LIMIT :limit")
    List<SightingEntity> getPending(String[] statuses, int limit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SightingEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<SightingEntity> entities);

    @Update
    void update(SightingEntity entity);

    @Query("DELETE FROM sightings WHERE localId = :localId")
    void delete(String localId);
}
