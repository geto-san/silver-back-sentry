package com.sbs.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AppNotificationDao {

    @Query("SELECT * FROM app_notifications WHERE recipientUserId = :recipientUserId ORDER BY createdAt DESC")
    LiveData<List<AppNotificationEntity>> observeByRecipient(String recipientUserId);

    @Query("SELECT * FROM app_notifications WHERE recipientUserId = :recipientUserId ORDER BY createdAt DESC")
    List<AppNotificationEntity> getByRecipient(String recipientUserId);

    @Query("SELECT COUNT(*) FROM app_notifications WHERE recipientUserId = :recipientUserId AND isRead = 0")
    LiveData<Integer> observeUnreadCount(String recipientUserId);

    @Query("SELECT * FROM app_notifications WHERE recipientUserId = :recipientUserId AND systemNotified = 0 ORDER BY createdAt ASC")
    List<AppNotificationEntity> getPendingSystemNotifications(String recipientUserId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(AppNotificationEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<AppNotificationEntity> entities);

    @Query("UPDATE app_notifications SET isRead = 1 WHERE recipientUserId = :recipientUserId AND notificationId = :notificationId")
    void markRead(String recipientUserId, String notificationId);

    @Query("UPDATE app_notifications SET isRead = 1 WHERE recipientUserId = :recipientUserId")
    void markAllRead(String recipientUserId);

    @Query("UPDATE app_notifications SET systemNotified = 1 WHERE notificationId = :notificationId")
    void markSystemNotified(String notificationId);
}
