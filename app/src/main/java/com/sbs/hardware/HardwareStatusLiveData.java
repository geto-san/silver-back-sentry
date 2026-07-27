package com.sbs.hardware;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import androidx.lifecycle.LiveData;

/**
 * HardwareStatusLiveData
 *
 * Java equivalent of Kotlin's callbackFlow { ... awaitClose { ... } } pattern
 * from NowInAndroid's ConnectivityManagerNetworkMonitor.
 *
 * How the lifecycle bridge works:
 *   onActive()   → called when the FIRST observer subscribes to this LiveData.
 *                  Registers a BroadcastReceiver for battery and airplane-mode
 *                  events, and immediately emits a snapshot of the current state.
 *   onInactive() → called when the LAST observer unsubscribes.
 *                  Unregisters the BroadcastReceiver to prevent memory leaks
 *                  and unnecessary wake-locks.
 *
 * This is the exact Java mirror of:
 *   override val state: Flow<HardwareState> = callbackFlow {
 *       val receiver = object : BroadcastReceiver() { ... }
 *       context.registerReceiver(receiver, filter)
 *       trySend(buildState())
 *       awaitClose { context.unregisterReceiver(receiver) }
 *   }
 *
 * Hardware checked (only what matters for SilverBack Sentry in the field):
 *   - GPS provider enabled          (sighting location tagging)
 *   - Network location provider     (fallback when GPS signal is weak)
 *   - Camera presence               (photo evidence capture)
 *   - Microphone presence           (audio note recording)
 *   - Cellular telephony hardware   (FCM / Firestore sync)
 *   - Network operator name         (which carrier is active)
 *   - Battery level + charging      (WorkManager upload gate)
 *   - Airplane mode on/off          (blocks all radios)
 *   - Available / total storage     (media capture feasibility)
 *
 * Usage:
 *   HardwareStatusLiveData liveData = new HardwareStatusLiveData(context);
 *   liveData.observe(lifecycleOwner, state -> { ... });
 */
public class HardwareStatusLiveData extends LiveData<HardwareState> {

    /** Battery below this threshold is considered "low" — mirrors WorkManager's own threshold. */
    private static final int BATTERY_LOW_THRESHOLD = 20;

    /** We flag storage as dangerously low below this value (in MB). */
    private static final long STORAGE_CRITICAL_MB = 50;

    private final Context appContext;
    private BroadcastReceiver eventReceiver;

    // ── Constructor ───────────────────────────────────────────────────────────

    public HardwareStatusLiveData(Context context) {
        this.appContext = context.getApplicationContext();
    }

    // ── LiveData lifecycle hooks ──────────────────────────────────────────────

    /**
     * Called when the first active observer subscribes.
     * Registers the broadcast receiver and emits the initial state immediately
     * so the observer never has to wait for the first event.
     */
    @Override
    protected void onActive() {
        eventReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Re-build and post a fresh snapshot on every relevant system event
                postValue(buildState());
            }
        };

        IntentFilter filter = new IntentFilter();
        // Battery state changes (level, charging plug in/out)
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        // Airplane mode toggled
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        // Location provider enabled/disabled (GPS toggled in Settings)
        filter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);

        appContext.registerReceiver(eventReceiver, filter);

        // Emit the current state immediately so the UI has data before the
        // first broadcast event fires — mirrors callbackFlow's initial trySend()
        postValue(buildState());
    }

    /**
     * Called when the last active observer unsubscribes.
     * Mirrors awaitClose { context.unregisterReceiver(receiver) } from callbackFlow.
     * Failing to unregister would leak the receiver until the process dies.
     */
    @Override
    protected void onInactive() {
        if (eventReceiver != null) {
            try {
                appContext.unregisterReceiver(eventReceiver);
            } catch (IllegalArgumentException ignored) {
                // Receiver was not registered (should not happen, but be defensive)
            }
            eventReceiver = null;
        }
    }

    // ── State snapshot builder ────────────────────────────────────────────────

    /**
     * Reads every hardware attribute synchronously and assembles a HardwareState.
     * This runs on the main thread (called from onActive or from the receiver's
     * onReceive), so all calls must complete in microseconds — no I/O allowed.
     */
    private HardwareState buildState() {

        // ── Location ──────────────────────────────────────────────────────────
        LocationManager lm = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
        boolean gpsAvailable = false;
        boolean networkLocAvailable = false;
        if (lm != null) {
            try {
                gpsAvailable     = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
                networkLocAvailable = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            } catch (Exception ignored) {
                // SecurityException can occur on some devices in rare cases
            }
        }

        // ── Camera ────────────────────────────────────────────────────────────
        PackageManager pm = appContext.getPackageManager();
        boolean cameraAvailable = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);

        // ── Microphone ────────────────────────────────────────────────────────
        boolean micAvailable = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE);

        // ── Cellular telephony ────────────────────────────────────────────────
        TelephonyManager tm = (TelephonyManager) appContext.getSystemService(Context.TELEPHONY_SERVICE);
        boolean cellularAvailable = false;
        String operatorName = "";
        if (tm != null) {
            cellularAvailable = tm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
            String name = tm.getNetworkOperatorName();
            operatorName = (name != null && !name.isEmpty()) ? name : "";
        }

        // ── Battery ───────────────────────────────────────────────────────────
        // ACTION_BATTERY_CHANGED is a sticky broadcast — registerReceiver with
        // null receiver returns the last broadcast intent without actually
        // registering anything. This is the canonical way to read battery state
        // without needing BATTERY_STATS permission.
        Intent batteryStatus = appContext.registerReceiver(
                null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        );

        int batteryLevel  = -1;
        boolean isCharging = false;
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) {
                batteryLevel = (int) ((level / (float) scale) * 100);
            }
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING
                       || status == BatteryManager.BATTERY_STATUS_FULL);
        }
        boolean isBatteryLow = batteryLevel >= 0 && batteryLevel < BATTERY_LOW_THRESHOLD;

        // ── Airplane mode ─────────────────────────────────────────────────────
        boolean airplaneMode = Settings.Global.getInt(
                appContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON,
                0
        ) != 0;

        // ── Internal storage ──────────────────────────────────────────────────
        long availableMb = 0;
        long totalMb     = 0;
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long blockSize    = stat.getBlockSizeLong();
            availableMb       = (stat.getAvailableBlocksLong() * blockSize) / (1024 * 1024);
            totalMb           = (stat.getBlockCountLong()     * blockSize) / (1024 * 1024);
        } catch (Exception ignored) {
            // StatFs can throw if the path is unavailable
        }

        return new HardwareState(
                gpsAvailable,
                networkLocAvailable,
                cameraAvailable,
                micAvailable,
                cellularAvailable,
                operatorName,
                batteryLevel,
                isBatteryLow,
                isCharging,
                airplaneMode,
                availableMb,
                totalMb
        );
    }
}
