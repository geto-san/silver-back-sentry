package com.sbs.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PatrolLogDao {

    @Query("SELECT * FROM patrol_logs WHERE rangerId = :rangerId ORDER BY timestamp DESC")
    LiveData<List<PatrolLogEntity>> observeAll(String rangerId);

    @Query("SELECT * FROM patrol_logs WHERE rangerId = :rangerId AND localId = :localId LIMIT 1")
    PatrolLogEntity getById(String rangerId, String localId);

    @Query("SELECT * FROM patrol_logs WHERE rangerId = :rangerId AND syncStatus IN (:statuses) ORDER BY timestamp ASC LIMIT :limit")
    List<PatrolLogEntity> getPending(String rangerId, String[] statuses, int limit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PatrolLogEntity entity);

    @Query("DELETE FROM patrol_logs WHERE rangerId = :rangerId AND localId = :localId")
    void delete(String rangerId, String localId);
}
