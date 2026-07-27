package com.sbs.app;

import android.app.Application;

import com.sbs.data.AppRepository;
import com.sbs.data.RealtimeSyncManager;
import com.sbs.data.SyncScheduler;
import com.sbs.notifications.AppNotificationHelper;

/**
 * SbsApplication — Application entry point for SilverBack Sentry.
 *
 * Startup chain (mirrors NowInAndroid's NiaApplication.onCreate()):
 *
 *   1. AppRepository.getInstance(this)
 *      Initialises the Room database singleton and runs any pending
 *      legacy data import (LegacyDataImporter.importIfNeeded) on the
 *      IO executor. Safe to call multiple times — returns the same instance.
 *
 *   2. AppNotificationHelper.ensureChannel(this)
 *      Creates the "sbs_shared_updates" NotificationChannel on Android 8+.
 *      Must be called before any notification is posted. Idempotent —
 *      calling it again when the channel already exists is a no-op.
 *
 *   3. SyncScheduler.startConnectivityMonitoring(this)
 *      Registers a ConnectivityManager.NetworkCallback for the app lifetime.
 *      When onAvailable() fires (device comes back online) it enqueues a
 *      WorkManager sync job via SyncScheduler.enqueueSync().
 *      This is the Q08 answer: zero polling, purely event-driven.
 *      The callback is registered once here and never unregistered (safe
 *      because it is scoped to the Application process lifetime, not an
 *      Activity).
 *
 *   4. SyncScheduler.scheduleConfiguredSync(this)
 *      Enqueues an initial one-time WorkManager sync on startup so that
 *      stale local data is refreshed and any PENDING records that survived
 *      a previous crash are uploaded as soon as the app starts.
 *      WorkManager constraints (CONNECTED + battery not low) gate the
 *      actual execution — this call just adds the job to the queue.
 *
 *   5. RealtimeSyncManager.getInstance(this).start()
 *      Attaches Firestore real-time snapshot listeners for:
 *        • shared_records  — sightings / patrol logs / health observations
 *          posted by other rangers
 *        • notifications/{uid}/items — in-app notification inbox
 *      These listeners fire immediately with cached data (offline-first)
 *      and then stream updates whenever Firestore connectivity is restored.
 *      start() is idempotent — calling it again for the same user ID is a
 *      no-op; it only re-registers when the user changes.
 *
 * Feature 5 — Lifecycle Controller:
 *   Application-level lifecycle hooks (onTrimMemory, onLowMemory) could
 *   be added here to flush in-progress Room writes or pause expensive
 *   background work when the system is under memory pressure.  These are
 *   left as extension points for now.
 *
 * Note on Hilt:
 *   This project does not use Hilt DI. All singletons (AppRepository,
 *   RealtimeSyncManager) use the double-checked locking singleton pattern
 *   and are accessed via getInstance(context). Workers use the @EntryPoint
 *   pattern to retrieve dependencies from the Hilt component graph without
 *   requiring @HiltAndroidApp here.
 *
 *   If Hilt is added in a future sprint, annotate this class with
 *   @HiltAndroidApp, inject HiltWorkerFactory, and implement
 *   Configuration.Provider to supply a custom WorkerFactory.
 */
public final class SbsApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. Prime the Room database singleton and kick off legacy import
        AppRepository.getInstance(this);

        // 2. Create notification channels before any notification is posted
        AppNotificationHelper.ensureChannel(this);

        // 3. Register the always-on connectivity callback
        //    → auto-enqueues a sync when the device goes from offline → online
        SyncScheduler.startConnectivityMonitoring(this);

        // 4. Enqueue a startup sync to catch up on any missed changes
        SyncScheduler.scheduleConfiguredSync(this);

        // 5. Attach Firestore real-time listeners (shared records + notifications)
        RealtimeSyncManager.getInstance(this).start();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // Future: pause non-critical background work when TRIM_MEMORY_RUNNING_LOW
        // or TRIM_MEMORY_UI_HIDDEN is received to help the system reclaim RAM.
    }
}
