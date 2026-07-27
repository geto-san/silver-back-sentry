package com.sbs.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertOrIgnore(AppNotificationEntity entity);

    @Update
    void update(AppNotificationEntity entity);

    @Transaction
    default void safeMerge(List<AppNotificationEntity> entities) {
        for (AppNotificationEntity entity : entities) {
            AppNotificationEntity existing = getById(entity.notificationId);
            if (existing == null) {
                insertOrIgnore(entity);
            } else {
                // Preserve the local systemNotified flag
                entity.systemNotified = existing.systemNotified;
                update(entity);
            }
        }
    }

    @Query("SELECT * FROM app_notifications WHERE notificationId = :id LIMIT 1")
    AppNotificationEntity getById(String id);

    @Query("UPDATE app_notifications SET isRead = 1 WHERE rangerId = :rangerId AND notificationId = :notificationId")
    void markRead(String rangerId, String notificationId);

    @Query("UPDATE app_notifications SET isRead = 1 WHERE rangerId = :rangerId")
    void markAllRead(String rangerId);

    @Query("UPDATE app_notifications SET systemNotified = 1 WHERE notificationId = :notificationId")
    void markSystemNotified(String notificationId);

    @Query("DELETE FROM app_notifications WHERE notificationId = :notificationId")
    void delete(String notificationId);
}
