package com.sbs.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PatrolLogDao {

    @Query("SELECT * FROM patrol_logs ORDER BY timestamp DESC")
    LiveData<List<PatrolLogEntity>> observeAll();

    @Query("SELECT * FROM patrol_logs WHERE localId = :localId LIMIT 1")
    PatrolLogEntity getById(String localId);

    @Query("SELECT * FROM patrol_logs WHERE syncStatus IN (:statuses) ORDER BY timestamp ASC LIMIT :limit")
    List<PatrolLogEntity> getPending(String[] statuses, int limit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PatrolLogEntity entity);

    @Query("DELETE FROM patrol_logs WHERE localId = :localId")
    void delete(String localId);
}
