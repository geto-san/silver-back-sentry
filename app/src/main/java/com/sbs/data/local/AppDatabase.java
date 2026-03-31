package com.sbs.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
                RangerEntity.class,
                SightingEntity.class,
                PatrolLogEntity.class,
                HealthObservationEntity.class,
                AppNotificationEntity.class
        },
        version = 4,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE sightings ADD COLUMN lastModifiedAt INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE patrol_logs ADD COLUMN lastModifiedAt INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `health_observations` (" +
                            "`localId` TEXT NOT NULL, `remoteId` TEXT, `authorId` TEXT, `authorName` TEXT, " +
                            "`title` TEXT, `notes` TEXT, `timestamp` INTEGER NOT NULL, `latitude` REAL NOT NULL, " +
                            "`longitude` REAL NOT NULL, `syncStatus` TEXT, `lastSyncAttempt` INTEGER NOT NULL, " +
                            "`lastModifiedAt` INTEGER NOT NULL, PRIMARY KEY(`localId`))"
            );
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_health_observations_remoteId` ON `health_observations` (`remoteId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_health_observations_timestamp` ON `health_observations` (`timestamp`)");
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_notifications` (" +
                            "`notificationId` TEXT NOT NULL, `recipientUserId` TEXT, `actorUserId` TEXT, `actorName` TEXT, " +
                            "`recordId` TEXT, `recordType` TEXT, `title` TEXT, `message` TEXT, `createdAt` INTEGER NOT NULL, " +
                            "`isRead` INTEGER NOT NULL, `destination` TEXT, `systemNotified` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`notificationId`))"
            );
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_app_notifications_recipientUserId` ON `app_notifications` (`recipientUserId`)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_app_notifications_recipientUserId_recordId_recordType` ON `app_notifications` (`recipientUserId`, `recordId`, `recordType`)");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Migrating health_observations: rename authorId to rangerId and add foreign key (requires recreate)
            database.execSQL("CREATE TABLE `health_observations_new` (`localId` TEXT NOT NULL, `remoteId` TEXT, `rangerId` TEXT NOT NULL, `authorName` TEXT, `title` TEXT, `notes` TEXT, `timestamp` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `syncStatus` TEXT, `lastSyncAttempt` INTEGER NOT NULL, `lastModifiedAt` INTEGER NOT NULL, PRIMARY KEY(`localId`), FOREIGN KEY(`rangerId`) REFERENCES `rangers`(`rangerId`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            database.execSQL("INSERT INTO `health_observations_new` SELECT localId, remoteId, authorId, authorName, title, notes, timestamp, latitude, longitude, syncStatus, lastSyncAttempt, lastModifiedAt FROM health_observations");
            database.execSQL("DROP TABLE `health_observations`");
            database.execSQL("ALTER TABLE `health_observations_new` RENAME TO `health_observations`");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_health_observations_remoteId` ON `health_observations` (`remoteId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_health_observations_timestamp` ON `health_observations` (`timestamp`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_health_observations_rangerId` ON `health_observations` (`rangerId`)");

            // Migrating app_notifications: rename recipientUserId to rangerId and add foreign key
            database.execSQL("CREATE TABLE `app_notifications_new` (`notificationId` TEXT NOT NULL, `rangerId` TEXT NOT NULL, `actorUserId` TEXT, `actorName` TEXT, `recordId` TEXT, `recordType` TEXT, `title` TEXT, `message` TEXT, `createdAt` INTEGER NOT NULL, `isRead` INTEGER NOT NULL, `destination` TEXT, `systemNotified` INTEGER NOT NULL, PRIMARY KEY(`notificationId`), FOREIGN KEY(`rangerId`) REFERENCES `rangers`(`rangerId`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            database.execSQL("INSERT INTO `app_notifications_new` SELECT notificationId, recipientUserId, actorUserId, actorName, recordId, recordType, title, message, createdAt, isRead, destination, systemNotified FROM app_notifications");
            database.execSQL("DROP TABLE `app_notifications`");
            database.execSQL("ALTER TABLE `app_notifications_new` RENAME TO `app_notifications`");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_app_notifications_rangerId` ON `app_notifications` (`rangerId`)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_app_notifications_rangerId_recordId_recordType` ON `app_notifications` (`rangerId`, `recordId`, `recordType`)");
        }
    };

    private static volatile AppDatabase instance;

    public abstract RangerDao rangerDao();

    public abstract SightingDao sightingDao();

    public abstract PatrolLogDao patrolLogDao();

    public abstract HealthObservationDao healthObservationDao();

    public abstract AppNotificationDao appNotificationDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "sbs.db"
                            )
                            .addMigrations(MIGRATION_1_2)
                            .addMigrations(MIGRATION_2_3)
                            .addMigrations(MIGRATION_3_4)
                            .build();
                }
            }
        }
        return instance;
    }
}
