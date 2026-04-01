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
