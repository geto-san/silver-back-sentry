package com.sbs.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * SilverBack Sentry — Room Database
 *
 * exportSchema = true  →  Room writes a JSON snapshot of the schema into
 *   app/schemas/ at compile time.  These files must be committed to git so
 *   that Room can verify every migration path at build time.
 *   Never set this to false in a production app — you lose the ability to
 *   validate that migrations are correct before shipping to rangers in the field.
 *
 * Migration strategy:
 *   • Simple column additions  →  addMigrations() with an explicit Migration
 *     object that runs ALTER TABLE.
 *   • Complex restructuring    →  same, but with multiple DDL statements
 *     inside a single migrate() body (Room wraps the whole block in a
 *     transaction for you).
 *   • NEVER call fallbackToDestructiveMigration() — rangers carry weeks of
 *     offline sightings that would be permanently destroyed.
 *
 * Version history:
 *   1 → 2  Added lastModifiedAt to sightings and patrol_logs
 *   2 → 3  Added app_notifications table
 *   3 → 4  Added lastModifiedAt to health_observations
 */
@Database(
        entities = {
                RangerEntity.class,
                SightingEntity.class,
                PatrolLogEntity.class,
                HealthObservationEntity.class,
                AppNotificationEntity.class
        },
        version = 4,
        exportSchema = true          // ← MUST be true; commit generated schemas/ to git
)
public abstract class AppDatabase extends RoomDatabase {

    // ── Migration 1 → 2 ──────────────────────────────────────────────────────
    // Added lastModifiedAt column to sightings and patrol_logs.
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(
                "ALTER TABLE sightings ADD COLUMN lastModifiedAt INTEGER NOT NULL DEFAULT 0"
            );
            db.execSQL(
                "ALTER TABLE patrol_logs ADD COLUMN lastModifiedAt INTEGER NOT NULL DEFAULT 0"
            );
        }
    };

    // ── Migration 2 → 3 ──────────────────────────────────────────────────────
    // Introduced the app_notifications table.
    // We recreate it here rather than relying on Room auto-detection because
    // the table has a ForeignKey constraint that requires the parent table
    // (rangers) to already exist.
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `app_notifications` ("
                + "`notificationId` TEXT NOT NULL, "
                + "`rangerId` TEXT NOT NULL, "
                + "`actorUserId` TEXT, "
                + "`actorName` TEXT, "
                + "`recordId` TEXT, "
                + "`recordType` TEXT, "
                + "`title` TEXT, "
                + "`message` TEXT, "
                + "`createdAt` INTEGER NOT NULL DEFAULT 0, "
                + "`isRead` INTEGER NOT NULL DEFAULT 0, "
                + "`destination` TEXT, "
                + "`systemNotified` INTEGER NOT NULL DEFAULT 0, "
                + "PRIMARY KEY(`notificationId`), "
                + "FOREIGN KEY(`rangerId`) REFERENCES `rangers`(`rangerId`) "
                + "  ON DELETE CASCADE ON UPDATE NO ACTION"
                + ")"
            );
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_app_notifications_rangerId` "
                + "ON `app_notifications` (`rangerId`)"
            );
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS "
                + "`index_app_notifications_rangerId_recordId_recordType` "
                + "ON `app_notifications` (`rangerId`, `recordId`, `recordType`)"
            );
        }
    };

    // ── Migration 3 → 4 ──────────────────────────────────────────────────────
    // Added lastModifiedAt column to health_observations so that the merge
    // logic in AppRepository can resolve conflicts between local edits and
    // records pulled from Firestore.
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(
                "ALTER TABLE health_observations ADD COLUMN lastModifiedAt INTEGER NOT NULL DEFAULT 0"
            );
        }
    };

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static volatile AppDatabase instance;

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "sbs.db"
                            )
                            // Register every migration — no gaps allowed.
                            // A missing migration would force Room to throw an
                            // IllegalStateException on upgrade, which is far
                            // preferable to silently destroying ranger data.
                            .addMigrations(
                                    MIGRATION_1_2,
                                    MIGRATION_2_3,
                                    MIGRATION_3_4
                            )
                            // Deliberately omitted: fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }

    // ── DAO accessors ─────────────────────────────────────────────────────────

    public abstract RangerDao            rangerDao();
    public abstract SightingDao          sightingDao();
    public abstract PatrolLogDao         patrolLogDao();
    public abstract HealthObservationDao healthObservationDao();
    public abstract AppNotificationDao   appNotificationDao();
}
