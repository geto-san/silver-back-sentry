package com.sbs.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.annotation.RequiresApi;
import android.os.Build;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.sbs.R;
import com.sbs.data.AppSettingsManager;

public class SettingsActivity extends BaseActivity {

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
                actThemeMode.setText(getString(R.string.light), false);
                break;
            case AppSettingsManager.THEME_DARK:
                actThemeMode.setText(getString(R.string.dark), false);
                break;
            default:
                actThemeMode.setText(getString(R.string.system_default), false);
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

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
    }
}
