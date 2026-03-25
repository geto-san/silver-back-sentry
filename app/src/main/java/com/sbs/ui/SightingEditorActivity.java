package com.sbs.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.sbs.R;
import com.sbs.data.AppSettingsManager;
import com.sbs.data.SightingRecord;
import com.sbs.data.SightingStore;
import com.sbs.data.SightingSyncManager;

public class SightingEditorActivity extends BaseActivity {

    private TextInputEditText etTitle;
    private TextInputEditText etNotes;
    private TextInputEditText etRadius;
    private double lat;
    private double lng;
    private AppSettingsManager appSettingsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sighting_editor);
        applyWindowInsets(findViewById(R.id.toolbar).getRootView());

        appSettingsManager = new AppSettingsManager(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etTitle = findViewById(R.id.etSightingTitle);
        etNotes = findViewById(R.id.etNotes);
        etRadius = findViewById(R.id.etRadius);
        MaterialButton btnSave = findViewById(R.id.btnSaveSighting);

        lat = getIntent().getDoubleExtra("lat", 0.0);
        lng = getIntent().getDoubleExtra("lng", 0.0);

        btnSave.setOnClickListener(v -> saveSighting());
        
        findViewById(R.id.btnCapturePhoto).setOnClickListener(v -> Toast.makeText(this, "Photo capture not implemented", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnRecordVideo).setOnClickListener(v -> Toast.makeText(this, "Video record not implemented", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnRecordAudio).setOnClickListener(v -> Toast.makeText(this, "Audio record not implemented", Toast.LENGTH_SHORT).show());
    }

    private void saveSighting() {
        String title = valueOf(etTitle);
        String notes = valueOf(etNotes);
        String radiusText = valueOf(etRadius);

        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(notes)) {
            Toast.makeText(this, "Please provide at least a title or description", Toast.LENGTH_SHORT).show();
            return;
        }

        float radius = 0;
        try {
            if (!TextUtils.isEmpty(radiusText)) radius = Float.parseFloat(radiusText);
        } catch (NumberFormatException ignored) {}

        String authorId = SightingSyncManager.resolveAuthorId();
        String authorName = SightingSyncManager.resolveAuthorName();
        
        SightingRecord record = SightingStore.createSighting(
                this, title, notes, lat, lng, System.currentTimeMillis(),
                authorId, authorName, null, null, null, radius
        );

        if (appSettingsManager.isAutoSyncEnabled() && SightingSyncManager.isOnline(this)) {
            SightingSyncManager.syncSighting(this, record);
        }

        setResult(RESULT_OK);
        finish();
    }
}
