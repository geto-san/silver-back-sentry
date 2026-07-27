package com.sbs.hardware;

/**
 * Immutable snapshot of all hardware components relevant to a field ranger.
 *
 * Produced by {@link HardwareStatusLiveData} and exposed to the UI via
 * {@link com.sbs.ui.DeviceInfoViewModel}.  Every field reflects the state
 * at the moment the snapshot was taken; the LiveData will emit a fresh
 * instance whenever any component changes.
 *
 * Only hardware that matters in the SilverBack Sentry context is included:
 *   - GPS        → required for sighting location tagging
 *   - Camera     → required for photo evidence capture
 *   - Microphone → required for audio-note recording
 *   - Cellular   → required for FCM / Firestore sync
 *   - Battery    → gates the WorkManager upload constraint
 *   - Airplane   → blocks all radios, prevents sync entirely
 */
public final class HardwareState {

    // ── GPS ───────────────────────────────────────────────────────────────────
    /** True if the device has GPS hardware AND the provider is currently enabled. */
    public final boolean gpsAvailable;
    /** True if the device has a Network (cell/Wi-Fi) location provider enabled. */
    public final boolean networkLocationAvailable;

    // ── Camera ────────────────────────────────────────────────────────────────
    /** True if at least one camera (front or rear) is present on the device. */
    public final boolean cameraAvailable;

    // ── Microphone ────────────────────────────────────────────────────────────
    /** True if a microphone is reported by the package manager. */
    public final boolean microphoneAvailable;

    // ── Cellular radio ────────────────────────────────────────────────────────
    /** True if the device has any telephony hardware (GSM / CDMA / LTE / NR). */
    public final boolean cellularAvailable;
    /**
     * Human-readable network operator name (e.g. "MTN Uganda"), or an empty
     * string when the SIM is absent / roaming with no name available.
     */
    public final String networkOperatorName;

    // ── Battery ───────────────────────────────────────────────────────────────
    /** Battery level in the range [0, 100]. -1 means the level could not be read. */
    public final int batteryPercent;
    /**
     * True when {@link #batteryPercent} is below the 20 % threshold used by
     * {@link androidx.work.Constraints.Builder#setRequiresBatteryNotLow}.
     * WorkManager's own constraint mirrors this value, but we expose it here
     * so the UI can warn the ranger before a sync attempt is rejected.
     */
    public final boolean isBatteryLow;
    /** True if the device is currently charging (AC, USB, or wireless). */
    public final boolean isCharging;

    // ── Airplane mode ─────────────────────────────────────────────────────────
    /**
     * True when airplane mode is on.  In this state cellular AND Wi-Fi radios
     * are disabled, making any Firestore or FCM communication impossible.
     */
    public final boolean isAirplaneModeOn;

    // ── Storage ───────────────────────────────────────────────────────────────
    /**
     * Available internal storage in megabytes.  Low storage can prevent media
     * capture (photos, audio, video) and Room database writes.
     */
    public final long availableStorageMb;
    /**
     * Total internal storage in megabytes.
     */
    public final long totalStorageMb;

    // ── Constructor ───────────────────────────────────────────────────────────

    public HardwareState(
            boolean gpsAvailable,
            boolean networkLocationAvailable,
            boolean cameraAvailable,
            boolean microphoneAvailable,
            boolean cellularAvailable,
            String networkOperatorName,
            int batteryPercent,
            boolean isBatteryLow,
            boolean isCharging,
            boolean isAirplaneModeOn,
            long availableStorageMb,
            long totalStorageMb
    ) {
        this.gpsAvailable            = gpsAvailable;
        this.networkLocationAvailable = networkLocationAvailable;
        this.cameraAvailable         = cameraAvailable;
        this.microphoneAvailable     = microphoneAvailable;
        this.cellularAvailable       = cellularAvailable;
        this.networkOperatorName     = networkOperatorName != null ? networkOperatorName : "";
        this.batteryPercent          = batteryPercent;
        this.isBatteryLow            = isBatteryLow;
        this.isCharging              = isCharging;
        this.isAirplaneModeOn        = isAirplaneModeOn;
        this.availableStorageMb      = availableStorageMb;
        this.totalStorageMb          = totalStorageMb;
    }

    // ── Convenience helpers ───────────────────────────────────────────────────

    /**
     * Returns true when the device is ready for a full sync:
     * online radio is available, battery is not critically low, and airplane
     * mode is off.  The WorkManager constraints mirror this check, but the UI
     * can use this flag to show a pre-flight warning before the ranger tries
     * to trigger a manual upload.
     */
    public boolean isReadyForSync() {
        return cellularAvailable && !isBatteryLow && !isAirplaneModeOn;
    }

    /**
     * Returns true when the device can capture media (camera present and
     * sufficient storage remains — we use 50 MB as the minimum threshold).
     */
    public boolean isReadyForMediaCapture() {
        return cameraAvailable && availableStorageMb >= 50;
    }

    /**
     * Battery level as a formatted string, e.g. "73%" or "Unknown".
     */
    public String batteryPercentLabel() {
        return batteryPercent >= 0 ? batteryPercent + "%" : "Unknown";
    }

    /**
     * Storage summary, e.g. "1 240 MB / 32 000 MB".
     */
    public String storageLabel() {
        return availableStorageMb + " MB / " + totalStorageMb + " MB";
    }
}
