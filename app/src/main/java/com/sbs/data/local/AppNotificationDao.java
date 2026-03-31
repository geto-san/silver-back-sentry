package com.sbs.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AppNotificationDao {

    @Query("SELECT * FROM app_notifications WHERE rangerId = :rangerId ORDER BY createdAt DESC")
    LiveData<List<AppNotificationEntity>> observeByRanger(String rangerId);

    @Query("SELECT * FROM app_notifications WHERE rangerId = :rangerId ORDER BY createdAt DESC")
    List<AppNotificationEntity> getByRanger(String rangerId);

    @Query("SELECT COUNT(*) FROM app_notifications WHERE rangerId = :rangerId AND isRead = 0")
    LiveData<Integer> observeUnreadCount(String rangerId);

    @Query("SELECT * FROM app_notifications WHERE rangerId = :rangerId AND systemNotified = 0 ORDER BY createdAt ASC")
    List<AppNotificationEntity> getPendingSystemNotifications(String rangerId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(AppNotificationEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<AppNotificationEntity> entities);

    @Query("UPDATE app_notifications SET isRead = 1 WHERE rangerId = :rangerId AND notificationId = :notificationId")
    void markRead(String rangerId, String notificationId);

    @Query("UPDATE app_notifications SET isRead = 1 WHERE rangerId = :rangerId")
    void markAllRead(String rangerId);

    @Query("UPDATE app_notifications SET systemNotified = 1 WHERE notificationId = :notificationId")
    void markSystemNotified(String notificationId);
}
