package com.sbs.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.sbs.R;
import com.sbs.data.AppRepository;
import com.sbs.data.HealthObservationRecord;
import com.sbs.data.SyncScheduler;

public final class HealthObservationEditorActivity extends BaseActivity {

    private AppRepository repository;
    private String authorId;
    private String existingId;
    private HealthObservationRecord existingRecord;
    private double lat;
    private double lng;
    private TextInputEditText titleInput;
    private TextInputEditText notesInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_observation_editor);
        applyWindowInsets(findViewById(R.id.toolbar).getRootView());

        repository = AppRepository.getInstance(this);
        authorId = FirebaseAuth.getInstance().getUid();
        if (authorId == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        titleInput = findViewById(R.id.etHealthTitle);
        notesInput = findViewById(R.id.etHealthNotes);
        existingId = getIntent().getStringExtra("health_id");
        lat = getIntent().getDoubleExtra("lat", 0.0);
        lng = getIntent().getDoubleExtra("lng", 0.0);

        if (existingId != null) {
            repository.loadHealthObservation(existingId, record -> {
                existingRecord = record;
                if (record != null) {
                    toolbar.setTitle(R.string.edit_health_observation);
                    titleInput.setText(record.title);
                    notesInput.setText(record.notes);
                    lat = record.lat;
                    lng = record.lng;
                }
            });
        }

        findViewById(R.id.btnSaveHealthObservation).setOnClickListener(v -> save());
    }

    private void save() {
        String title = valueOf(titleInput);
        String notes = valueOf(notesInput);
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(notes)) {
            Toast.makeText(this, "Please add notes or a title", Toast.LENGTH_SHORT).show();
            return;
        }
        repository.saveHealthObservation(
                authorId,
                existingRecord != null ? existingRecord.localId : null,
                title,
                notes,
                existingRecord != null ? existingRecord.timestamp : System.currentTimeMillis(),
                lat,
                lng,
                record -> {
                    SyncScheduler.enqueueSync(this);
                    setResult(RESULT_OK);
                    finish();
                }
        );
    }
}
