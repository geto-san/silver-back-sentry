package com.sbs.ui;

import android.app.ActivityManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.sbs.R;
import com.sbs.hardware.HardwareState;

import java.util.Locale;

/**
 * DeviceInfoActivity — Feature 1: Hardware Diagnostic Utility
 *
 * Displays a live, reactive snapshot of every hardware component
 * relevant to a field ranger operating in a remote forest environment.
 *
 * Architecture:
 *   DeviceInfoViewModel (AndroidViewModel)
 *     └─ HardwareStatusLiveData (extends LiveData<HardwareState>)
 *         └─ BroadcastReceiver (battery, airplane-mode, GPS provider events)
 *             └─ DeviceInfoActivity.observe() → update UI rows
 *
 * Why this is better than the old static approach:
 *   The previous implementation called PackageManager / ActivityManager once
 *   in onCreate() and never updated.  If the ranger toggled GPS off, plugged
 *   in a charger, or switched to airplane mode while looking at this screen,
 *   the displayed values would be stale.
 *
 *   With HardwareStatusLiveData the screen automatically reflects the current
 *   device state — no polling required.  The BroadcastReceiver is registered
 *   in LiveData.onActive() (when the Activity is visible) and unregistered
 *   in onInactive() (when it goes to background), so there is zero battery
 *   drain while the screen is not displayed.
 *
 * Static information (Device Overview, CPU, RAM) is still populated once in
 * onCreate() because that information cannot change at runtime.
 *
 * Live information (GPS, Camera, Microphone, Cellular, Battery, Airplane,
 * Storage) is updated every time HardwareStatusLiveData emits a new snapshot.
 */
public class DeviceInfoActivity extends BaseActivity {

    // ── ViewModel ─────────────────────────────────────────────────────────────
    private DeviceInfoViewModel viewModel;

    // ── Live sensor section views ─────────────────────────────────────────────
    private LinearLayout sensorContainer;

    // Individual live rows (populated once, updated by observer)
    private View rowGps;
    private View rowNetworkLoc;
    private View rowCamera;
    private View rowMicrophone;
    private View rowCellular;
    private View rowOperator;
    private View rowBattery;
    private View rowCharging;
    private View rowAirplane;
    private View rowStorage;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_info);
        applyWindowInsets(findViewById(R.id.deviceInfoRoot));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // ── Static sections (never change at runtime) ─────────────────────────
        setupDeviceOverview();
        setupCpuDiagnostics();
        setupRamDiagnostics();

        // ── Live sensor section: inflate placeholder rows ─────────────────────
        setupSensorRowViews();

        // ── ViewModel + LiveData observation ─────────────────────────────────
        viewModel = new ViewModelProvider(this).get(DeviceInfoViewModel.class);

        // Observe: every time battery level, GPS, or airplane mode changes the
        // HardwareStatusLiveData emits a fresh HardwareState and we update all
        // live rows here.  No polling — purely event-driven.
        viewModel.getHardwareState().observe(this, this::applyHardwareState);
    }

    // ── Static section setup ──────────────────────────────────────────────────

    private void setupDeviceOverview() {
        setRowData(R.id.rowBrand,          "Brand",           Build.BRAND);
        setRowData(R.id.rowManufacturer,   "Manufacturer",    Build.MANUFACTURER);
        setRowData(R.id.rowModel,          "Model",           Build.MODEL);
        setRowData(R.id.rowDevice,         "Device",          Build.DEVICE);
        setRowData(R.id.rowAndroidVersion, "Android Version", Build.VERSION.RELEASE);
        setRowData(R.id.rowSdk,            "SDK Level",       String.valueOf(Build.VERSION.SDK_INT));
        setRowData(R.id.rowAppVersion,     "App Version",     getAppVersion());
    }

    private void setupCpuDiagnostics() {
        setRowData(R.id.rowCpuAbi,  "CPU ABI",          Build.SUPPORTED_ABIS[0]);
        setRowData(R.id.rowCores,   "Processor Cores",  String.valueOf(Runtime.getRuntime().availableProcessors()));
        setRowData(R.id.rowBoard,   "Board",            Build.BOARD);
        setRowData(R.id.rowHardware,"Hardware",         Build.HARDWARE);
    }

    private void setupRamDiagnostics() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(mi);

        double totalRam = mi.totalMem  / (1024.0 * 1024.0 * 1024.0);
        double availRam = mi.availMem  / (1024.0 * 1024.0 * 1024.0);

        setRowData(R.id.rowTotalRam,     "Total RAM",         String.format(Locale.US, "%.2f GB", totalRam));
        setRowData(R.id.rowAvailableRam, "Available RAM",     String.format(Locale.US, "%.2f GB", availRam));
        setRowData(R.id.rowLowMemory,    "Low Memory Status", mi.lowMemory ? "YES ⚠" : "NO");
    }

    // ── Live sensor section ───────────────────────────────────────────────────

    /**
     * Inflates a diagnostic row for each live hardware attribute and stores
     * a reference to each View so that applyHardwareState() can update them
     * without calling findViewById() on every emission.
     */
    private void setupSensorRowViews() {
        sensorContainer = findViewById(R.id.sensorContainer);
        sensorContainer.removeAllViews();

        rowGps        = inflateLiveRow("GPS Provider",           "Checking…");
        rowNetworkLoc = inflateLiveRow("Network Location",       "Checking…");
        rowCamera     = inflateLiveRow("Camera",                 "Checking…");
        rowMicrophone = inflateLiveRow("Microphone",             "Checking…");
        rowCellular   = inflateLiveRow("Cellular Radio",         "Checking…");
        rowOperator   = inflateLiveRow("Network Operator",       "Checking…");
        rowBattery    = inflateLiveRow("Battery Level",          "Checking…");
        rowCharging   = inflateLiveRow("Charging Status",        "Checking…");
        rowAirplane   = inflateLiveRow("Airplane Mode",          "Checking…");
        rowStorage    = inflateLiveRow("Available Storage",      "Checking…");
    }

    /**
     * Inflates a row_diagnostic layout, sets the label to the given name,
     * sets the initial value text, adds it to sensorContainer, and returns
     * the inflated View so the caller can hold a reference for later updates.
     */
    private View inflateLiveRow(String label, String initialValue) {
        View row = getLayoutInflater().inflate(R.layout.row_diagnostic, sensorContainer, false);
        ((TextView) row.findViewById(R.id.tvLabel)).setText(label);
        ((TextView) row.findViewById(R.id.tvValue)).setText(initialValue);
        View subValue = row.findViewById(R.id.tvSubValue);
        if (subValue != null) subValue.setVisibility(View.GONE);
        sensorContainer.addView(row);
        return row;
    }

    // ── LiveData observer ─────────────────────────────────────────────────────

    /**
     * Called every time HardwareStatusLiveData emits a new snapshot.
     *
     * This is the entire rendering contract for live data — every row that
     * can change at runtime is updated here.  The method is called on the
     * main thread by LiveData, so direct view mutations are safe.
     *
     * Colour coding convention:
     *   Green  = hardware present and functioning correctly
     *   Red    = hardware absent or a condition that blocks field operations
     *   Orange = degraded / warning state (battery low, no GPS but network loc OK)
     */
    private void applyHardwareState(HardwareState state) {
        if (state == null) return;

        // ── GPS ───────────────────────────────────────────────────────────────
        updateAvailabilityRow(rowGps,
                state.gpsAvailable,
                "Enabled",
                "Disabled — tap to enable in Settings");

        updateAvailabilityRow(rowNetworkLoc,
                state.networkLocationAvailable,
                "Enabled (fallback)",
                "Disabled");

        // ── Camera ────────────────────────────────────────────────────────────
        updateAvailabilityRow(rowCamera,
                state.cameraAvailable,
                "Available",
                "Not available on this device");

        // ── Microphone ────────────────────────────────────────────────────────
        updateAvailabilityRow(rowMicrophone,
                state.microphoneAvailable,
                "Available",
                "Not available on this device");

        // ── Cellular ──────────────────────────────────────────────────────────
        updateAvailabilityRow(rowCellular,
                state.cellularAvailable,
                "Present",
                "No telephony hardware");

        updateTextRow(rowOperator,
                state.networkOperatorName.isEmpty() ? "Unknown / No SIM" : state.networkOperatorName);

        // ── Battery ───────────────────────────────────────────────────────────
        String batteryText = state.batteryPercentLabel();
        if (state.isBatteryLow) {
            batteryText += "  ⚠ LOW — upload may be blocked";
        }
        int batteryColour = state.isBatteryLow
                ? getResources().getColor(android.R.color.holo_orange_dark, getTheme())
                : getResources().getColor(android.R.color.holo_green_dark, getTheme());
        updateValueRow(rowBattery, batteryText, batteryColour);

        // ── Charging ──────────────────────────────────────────────────────────
        updateAvailabilityRow(rowCharging,
                state.isCharging,
                "Charging ⚡",
                "On battery");

        // ── Airplane mode ─────────────────────────────────────────────────────
        if (state.isAirplaneModeOn) {
            int red = getResources().getColor(android.R.color.holo_red_dark, getTheme());
            updateValueRow(rowAirplane,
                    "ON — all radios disabled, sync impossible", red);
        } else {
            int green = getResources().getColor(android.R.color.holo_green_dark, getTheme());
            updateValueRow(rowAirplane, "OFF", green);
        }

        // ── Storage ───────────────────────────────────────────────────────────
        boolean storageCritical = state.availableStorageMb < 50;
        String storageText = state.storageLabel()
                + (storageCritical ? "  ⚠ LOW" : "");
        int storageColour = storageCritical
                ? getResources().getColor(android.R.color.holo_orange_dark, getTheme())
                : getResources().getColor(android.R.color.holo_green_dark, getTheme());
        updateValueRow(rowStorage, storageText, storageColour);
    }

    // ── Row update helpers ────────────────────────────────────────────────────

    /**
     * Updates a row with green/red colour based on whether the capability
     * is available.
     */
    private void updateAvailabilityRow(View row,
                                       boolean available,
                                       String availableLabel,
                                       String unavailableLabel) {
        TextView tvValue = row.findViewById(R.id.tvValue);
        if (available) {
            tvValue.setText(availableLabel);
            tvValue.setTextColor(
                    getResources().getColor(android.R.color.holo_green_dark, getTheme()));
        } else {
            tvValue.setText(unavailableLabel);
            tvValue.setTextColor(
                    getResources().getColor(android.R.color.holo_red_dark, getTheme()));
        }
    }

    /**
     * Updates a row with a given text value using the default secondary text colour.
     */
    private void updateTextRow(View row, String text) {
        ((TextView) row.findViewById(R.id.tvValue)).setText(text);
    }

    /**
     * Updates a row with a given text value and an explicit text colour.
     */
    private void updateValueRow(View row, String text, int colour) {
        TextView tv = row.findViewById(R.id.tvValue);
        tv.setText(text);
        tv.setTextColor(colour);
    }

    // ── Static row helper ─────────────────────────────────────────────────────

    /**
     * Sets the label and value for an include'd row_diagnostic view that is
     * already part of the layout (static rows only — not the inflated live rows).
     */
    private void setRowData(int layoutId, String label, String value) {
        View layout = findViewById(layoutId);
        if (layout == null) return;
        ((TextView) layout.findViewById(R.id.tvLabel)).setText(label);
        ((TextView) layout.findViewById(R.id.tvValue)).setText(value);
        View subValue = layout.findViewById(R.id.tvSubValue);
        if (subValue != null) subValue.setVisibility(View.GONE);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String getAppVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "Unknown";
        }
    }
}
