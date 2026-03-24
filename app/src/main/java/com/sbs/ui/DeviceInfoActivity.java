package com.sbs.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.sbs.R;

import java.util.Locale;

public class DeviceInfoActivity extends BaseActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope, lightSensor;
    
    // Live sensor TextViews
    private TextView tvLiveAccel, tvLiveGyro, tvLiveLight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_info);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        setupDeviceOverview();
        setupCpuDiagnostics();
        setupRamDiagnostics();
        setupSensorDiagnostics();
        setupLiveSensors();
    }

    private void setupDeviceOverview() {
        setRowData(R.id.rowBrand, "Brand", Build.BRAND);
        setRowData(R.id.rowManufacturer, "Manufacturer", Build.MANUFACTURER);
        setRowData(R.id.rowModel, "Model", Build.MODEL);
        setRowData(R.id.rowDevice, "Device", Build.DEVICE);
        setRowData(R.id.rowAndroidVersion, "Android Version", Build.VERSION.RELEASE);
        setRowData(R.id.rowSdk, "SDK Level", String.valueOf(Build.VERSION.SDK_INT));
        setRowData(R.id.rowAppVersion, "App Version", getAppVersion());
    }

    private void setupCpuDiagnostics() {
        setRowData(R.id.rowCpuAbi, "CPU ABI", Build.SUPPORTED_ABIS[0]);
        setRowData(R.id.rowCores, "Processor Cores", String.valueOf(Runtime.getRuntime().availableProcessors()));
        setRowData(R.id.rowBoard, "Board", Build.BOARD);
        setRowData(R.id.rowHardware, "Hardware", Build.HARDWARE);
    }

    private void setupRamDiagnostics() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(mi);

        double totalRam = mi.totalMem / (1024.0 * 1024.0 * 1024.0);
        double availRam = mi.availMem / (1024.0 * 1024.0 * 1024.0);

        setRowData(R.id.rowTotalRam, "Total RAM", String.format(Locale.US, "%.2f GB", totalRam));
        setRowData(R.id.rowAvailableRam, "Available RAM", String.format(Locale.US, "%.2f GB", availRam));
        setRowData(R.id.rowLowMemory, "Low Memory Status", mi.lowMemory ? "YES" : "NO");
    }

    private void setupSensorDiagnostics() {
        LinearLayout container = findViewById(R.id.sensorContainer);
        container.removeAllViews();

        int[] sensorTypes = {
                Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD,
                Sensor.TYPE_PROXIMITY, Sensor.TYPE_LIGHT, Sensor.TYPE_PRESSURE,
                Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR, Sensor.TYPE_ROTATION_VECTOR,
                Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_AMBIENT_TEMPERATURE,
                Sensor.TYPE_RELATIVE_HUMIDITY
        };

        String[] sensorNames = {
                "Accelerometer", "Gyroscope", "Magnetometer",
                "Proximity Sensor", "Light Sensor", "Pressure / Barometer",
                "Step Counter", "Step Detector", "Rotation Vector",
                "Gravity Sensor", "Linear Acceleration", "Ambient Temperature",
                "Relative Humidity"
        };

        for (int i = 0; i < sensorTypes.length; i++) {
            Sensor s = sensorManager.getDefaultSensor(sensorTypes[i]);
            addSensorRow(container, sensorNames[i], s);
        }
    }

    private void addSensorRow(LinearLayout container, String name, Sensor s) {
        View row = getLayoutInflater().inflate(R.layout.row_diagnostic, container, false);
        TextView tvLabel = row.findViewById(R.id.tvLabel);
        TextView tvValue = row.findViewById(R.id.tvValue);
        TextView tvSubValue = row.findViewById(R.id.tvSubValue);

        tvLabel.setText(name);
        if (s != null) {
            tvValue.setText("Available");
            tvValue.setTextColor(getResources().getColor(android.R.color.holo_green_dark, getTheme()));
            tvSubValue.setText(String.format("%s (v%d)", s.getVendor(), s.getVersion()));
            tvSubValue.setVisibility(View.VISIBLE);
        } else {
            tvValue.setText("Not Available");
            tvValue.setTextColor(getResources().getColor(android.R.color.holo_red_dark, getTheme()));
        }
        container.addView(row);
    }

    private void setupLiveSensors() {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        boolean anyLive = false;

        if (accelerometer != null) {
            setRowData(R.id.rowLiveAccel, "Live Accelerometer", "Waiting for data...");
            tvLiveAccel = findViewById(R.id.rowLiveAccel).findViewById(R.id.tvValue);
            anyLive = true;
        } else {
            findViewById(R.id.rowLiveAccel).setVisibility(View.GONE);
        }

        if (gyroscope != null) {
            setRowData(R.id.rowLiveGyro, "Live Gyroscope", "Waiting for data...");
            tvLiveGyro = findViewById(R.id.rowLiveGyro).findViewById(R.id.tvValue);
            anyLive = true;
        } else {
            findViewById(R.id.rowLiveGyro).setVisibility(View.GONE);
        }

        if (lightSensor != null) {
            setRowData(R.id.rowLiveLight, "Live Light Sensor", "Waiting for data...");
            tvLiveLight = findViewById(R.id.rowLiveLight).findViewById(R.id.tvValue);
            anyLive = true;
        } else {
            findViewById(R.id.rowLiveLight).setVisibility(View.GONE);
        }

        if (!anyLive) {
            findViewById(R.id.cardLiveSensors).setVisibility(View.GONE);
        }
    }

    private void setRowData(int layoutId, String label, String value) {
        View layout = findViewById(layoutId);
        ((TextView) layout.findViewById(R.id.tvLabel)).setText(label);
        ((TextView) layout.findViewById(R.id.tvValue)).setText(value);
    }

    private String getAppVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "Unknown";
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        if (gyroscope != null) sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI);
        if (lightSensor != null) sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && tvLiveAccel != null) {
            tvLiveAccel.setText(String.format(Locale.US, "X: %.2f, Y: %.2f, Z: %.2f", event.values[0], event.values[1], event.values[2]));
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && tvLiveGyro != null) {
            tvLiveGyro.setText(String.format(Locale.US, "X: %.2f, Y: %.2f, Z: %.2f", event.values[0], event.values[1], event.values[2]));
        } else if (event.sensor.getType() == Sensor.TYPE_LIGHT && tvLiveLight != null) {
            tvLiveLight.setText(String.format(Locale.US, "%.1f lx", event.values[0]));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
