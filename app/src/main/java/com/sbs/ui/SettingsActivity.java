package com.sbs.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.sbs.R;
import com.sbs.data.AppSettingsManager;

public class SettingsActivity extends BaseActivity {

    private TextView tvThemeMode;
    private TextView tvNotificationStatus;
    private TextView tvLocationStatus;

    private AutoCompleteTextView actThemeMode;
    private AutoCompleteTextView actSyncInterval;
    private SwitchMaterial switchAutoSync;
    private SwitchMaterial switchWifiOnlySync;
    private SwitchMaterial switchAutoCenterMap;
    private SwitchMaterial switchShowSampleMarkers;

    private AppSettingsManager appSettingsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appSettingsManager = new AppSettingsManager(this);
        appSettingsManager.applyTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        applyWindowInsets(findViewById(R.id.toolbar).getRootView());

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        MaterialButton btnAppSettings = findViewById(R.id.btnAppSettings);
        MaterialButton btnNotificationSettings = findViewById(R.id.btnNotificationSettings);
        MaterialButton btnLocationSettings = findViewById(R.id.btnLocationSettings);

        tvThemeMode = findViewById(R.id.tvThemeMode);
        tvNotificationStatus = findViewById(R.id.tvNotificationStatus);
        tvLocationStatus = findViewById(R.id.tvLocationStatus);

        actThemeMode = findViewById(R.id.actThemeMode);
        actSyncInterval = findViewById(R.id.actSyncInterval);
        switchAutoSync = findViewById(R.id.switchAutoSync);
        switchWifiOnlySync = findViewById(R.id.switchWifiOnlySync);
        switchAutoCenterMap = findViewById(R.id.switchAutoCenterMap);
        switchShowSampleMarkers = findViewById(R.id.switchShowSampleMarkers);

        toolbar.setNavigationOnClickListener(v -> finish());

        setupThemeDropdown();
        setupSyncIntervalDropdown();
        bindSavedValues();
        bindSettingListeners();

        btnAppSettings.setOnClickListener(v -> openAppSettings());
        btnNotificationSettings.setOnClickListener(v -> openNotificationSettings());
        btnLocationSettings.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindSystemStatus();
    }

    private void setupThemeDropdown() {
        String[] themeOptions = {"System default", "Light", "Dark"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, themeOptions);
        actThemeMode.setAdapter(adapter);
    }

    private void setupSyncIntervalDropdown() {
        String[] syncOptions = {
                AppSettingsManager.SYNC_INTERVAL_15,
                AppSettingsManager.SYNC_INTERVAL_30,
                AppSettingsManager.SYNC_INTERVAL_60,
                AppSettingsManager.SYNC_INTERVAL_MANUAL
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, syncOptions);
        actSyncInterval.setAdapter(adapter);
    }

    private void bindSavedValues() {
        switchAutoSync.setChecked(appSettingsManager.isAutoSyncEnabled());
        switchWifiOnlySync.setChecked(appSettingsManager.isWifiOnlySyncEnabled());
        switchAutoCenterMap.setChecked(appSettingsManager.isAutoCenterMapEnabled());
        switchShowSampleMarkers.setChecked(appSettingsManager.isShowSampleMarkersEnabled());

        String themeMode = appSettingsManager.getThemeMode();
        switch (themeMode) {
            case AppSettingsManager.THEME_LIGHT:
                actThemeMode.setText("Light", false);
                break;
            case AppSettingsManager.THEME_DARK:
                actThemeMode.setText("Dark", false);
                break;
            default:
                actThemeMode.setText("System default", false);
                break;
        }

        actSyncInterval.setText(appSettingsManager.getSyncInterval(), false);
        bindSyncControlState();
    }

    private void bindSettingListeners() {
        actThemeMode.setOnItemClickListener((parent, view, position, id) -> {
            String selected = actThemeMode.getText() == null ? "" : actThemeMode.getText().toString();
            if ("Light".equals(selected)) {
                appSettingsManager.setThemeMode(AppSettingsManager.THEME_LIGHT);
            } else if ("Dark".equals(selected)) {
                appSettingsManager.setThemeMode(AppSettingsManager.THEME_DARK);
            } else {
                appSettingsManager.setThemeMode(AppSettingsManager.THEME_SYSTEM);
            }

            appSettingsManager.applyTheme();
        });

        actSyncInterval.setOnItemClickListener((parent, view, position, id) ->
                appSettingsManager.setSyncInterval(actSyncInterval.getText().toString()));

        switchAutoSync.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appSettingsManager.setAutoSyncEnabled(isChecked);
            bindSyncControlState();
        });

        switchWifiOnlySync.setOnCheckedChangeListener((buttonView, isChecked) ->
                appSettingsManager.setWifiOnlySyncEnabled(isChecked));

        switchAutoCenterMap.setOnCheckedChangeListener((buttonView, isChecked) ->
                appSettingsManager.setAutoCenterMapEnabled(isChecked));

        switchShowSampleMarkers.setOnCheckedChangeListener((buttonView, isChecked) ->
                appSettingsManager.setShowSampleMarkersEnabled(isChecked));
    }

    private void bindSyncControlState() {
        boolean autoSyncEnabled = switchAutoSync.isChecked();
        switchWifiOnlySync.setEnabled(autoSyncEnabled);
        actSyncInterval.setEnabled(autoSyncEnabled);
        actSyncInterval.setAlpha(autoSyncEnabled ? 1f : 0.5f);
        switchWifiOnlySync.setAlpha(autoSyncEnabled ? 1f : 0.5f);
    }

    private void bindSystemStatus() {
        int nightMode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        tvThemeMode.setText(nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
                ? "Dark"
                : "Light");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
            tvNotificationStatus.setText(granted ? "Allowed" : "Blocked");
        } else {
            tvNotificationStatus.setText("Allowed by system version");
        }

        boolean locationGranted =
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;

        tvLocationStatus.setText(locationGranted ? "Allowed" : "Not granted");
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
    }
}
