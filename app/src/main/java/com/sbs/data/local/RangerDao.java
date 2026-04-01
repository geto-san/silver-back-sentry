package com.sbs.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.lifecycle.LiveData;

import java.util.List;

@Dao
public interface RangerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(RangerEntity ranger);

    @Query("SELECT * FROM rangers ORDER BY updatedAt DESC")
    LiveData<List<RangerEntity>> observeAll();

    @Query("SELECT * FROM rangers ORDER BY updatedAt DESC")
    List<RangerEntity> getAll();

    @Query("SELECT * FROM rangers WHERE rangerId = :rangerId LIMIT 1")
    RangerEntity getById(String rangerId);

    @Query("SELECT COUNT(*) FROM rangers WHERE rangerId = :rangerId")
    int countById(String rangerId);

    @Query("DELETE FROM rangers WHERE rangerId = :rangerId")
    void deleteById(String rangerId);
}
