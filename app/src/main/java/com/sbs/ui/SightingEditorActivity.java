package com.sbs.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.sbs.R;
import com.sbs.data.AppRepository;
import com.sbs.data.SightingRecord;
import com.sbs.data.SyncScheduler;

public class SightingEditorActivity extends BaseActivity {

    private TextInputEditText etTitle;
    private TextInputEditText etNotes;
    private TextInputEditText etRadius;
    private double lat;
    private double lng;
    private String existingSightingId;
    private SightingRecord existingRecord;
    private AppRepository repository;
    private String authorId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sighting_editor);
        applyWindowInsets(findViewById(R.id.toolbar).getRootView());

        repository = AppRepository.getInstance(this);
        authorId = FirebaseAuth.getInstance().getUid();
        if (authorId == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etTitle = findViewById(R.id.etSightingTitle);
        etNotes = findViewById(R.id.etNotes);
        etRadius = findViewById(R.id.etRadius);
        MaterialButton btnSave = findViewById(R.id.btnSaveSighting);

        existingSightingId = getIntent().getStringExtra("sighting_id");
        if (existingSightingId != null) {
            repository.loadSighting(existingSightingId, record -> {
                existingRecord = record;
                if (record == null) {
                    return;
                }
                toolbar.setTitle(R.string.edit_sighting);
                etTitle.setText(record.title);
                etNotes.setText(record.notes);
                etRadius.setText(String.valueOf(record.radius));
                lat = record.lat;
                lng = record.lng;
            });
        } else {
            lat = getIntent().getDoubleExtra("lat", 0.0);
            lng = getIntent().getDoubleExtra("lng", 0.0);
        }

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

        long timestamp = existingRecord != null ? existingRecord.timestamp : System.currentTimeMillis();
        repository.saveSighting(
                authorId,
                existingRecord != null ? existingRecord.localId : null,
                title,
                notes,
                lat,
                lng,
                timestamp,
                radius,
                existingRecord != null ? existingRecord.audioPath : null,
                existingRecord != null ? existingRecord.imagePath : null,
                existingRecord != null ? existingRecord.videoPath : null,
                record -> {
                    SyncScheduler.enqueueSync(this);
                    setResult(RESULT_OK);
                    finish();
                }
        );
    }

    private String valueOf(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }
}
