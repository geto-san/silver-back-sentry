package com.sbs.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.sbs.R;
import com.sbs.data.AppRepository;
import com.sbs.data.HealthObservationRecord;
import com.sbs.data.SyncScheduler;

/**
 * HealthObservationEditorActivity
 *
 * Create or edit a gorilla health observation record.
 *
 * Architecture (offline-first, single source of truth):
 *   - All writes go through AppRepository → Room (syncStatus = PENDING).
 *   - Room LiveData in HealthObservationsActivity / MapActivity auto-updates.
 *   - WorkManager upload is enqueued after save, gated by network + battery.
 *
 * Feature 4 — Location picking:
 *   "Pick Location on Map" button launches LocationPickerActivity for result.
 *   On RESULT_OK the returned lat/lng are stored and shown in tvLocationLabel.
 *   The coordinates are persisted with the record and shown as a pin on the
 *   map in DashboardActivity and MapActivity.
 *
 * Feature 6 — Material Design 3:
 *   Outlined TextInputLayout / TextInputEditText for all text fields.
 *   MaterialButton for all action buttons (outlined for secondary actions,
 *   filled InstagramButton style for the primary save action).
 */
public final class HealthObservationEditorActivity extends BaseActivity {

    // ── Views ─────────────────────────────────────────────────────────────────

    private TextInputEditText etTitle;
    private TextInputEditText etNotes;
    private TextView          tvLocationLabel;
    private MaterialButton    btnPickLocation;
    private MaterialButton    btnSave;

    // ── State ─────────────────────────────────────────────────────────────────

    private double selectedLat = 0.0;
    private double selectedLng = 0.0;

    private String existingId;
    private HealthObservationRecord existingRecord;

    private AppRepository repository;
    private String authorId;

    // ── Activity result launchers ─────────────────────────────────────────────

    /**
     * Launcher for LocationPickerActivity.
     *
     * On RESULT_OK, reads RESULT_LAT and RESULT_LNG from the returned Intent.
     * Updates selectedLat / selectedLng and refreshes tvLocationLabel.
     *
     * Mirrors the same launcher in SightingEditorActivity for consistency.
     */
    private final ActivityResultLauncher<Intent> locationPickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            double lat = result.getData()
                                    .getDoubleExtra(LocationPickerActivity.RESULT_LAT, 0.0);
                            double lng = result.getData()
                                    .getDoubleExtra(LocationPickerActivity.RESULT_LNG, 0.0);
                            if (lat != 0.0 || lng != 0.0) {
                                selectedLat = lat;
                                selectedLng = lng;
                                updateLocationLabel();
                            }
                        }
                    }
            );

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_observation_editor);
        applyWindowInsets(findViewById(R.id.toolbar).getRootView());

        repository = AppRepository.getInstance(this);
        authorId   = FirebaseAuth.getInstance().getUid();

        if (authorId == null) {
            Toast.makeText(this, "You must be signed in to log observations.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews();
        setupToolbar();
        setupClickListeners();

        // ── Load existing record if editing ──────────────────────────────────
        existingId = getIntent().getStringExtra("health_id");
        if (existingId != null) {
            loadExistingRecord();
        } else {
            // Pre-fill coordinates if the caller passed them (e.g. from MapActivity)
            selectedLat = getIntent().getDoubleExtra("lat", 0.0);
            selectedLng = getIntent().getDoubleExtra("lng", 0.0);
            updateLocationLabel();
        }
    }

    // ── View binding ──────────────────────────────────────────────────────────

    private void bindViews() {
        etTitle         = findViewById(R.id.etHealthTitle);
        etNotes         = findViewById(R.id.etHealthNotes);
        tvLocationLabel = findViewById(R.id.tvLocationLabel);
        btnPickLocation = findViewById(R.id.btnPickLocation);
        btnSave         = findViewById(R.id.btnSaveHealthObservation);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    // ── Click listeners ───────────────────────────────────────────────────────

    private void setupClickListeners() {
        if (btnPickLocation != null) {
            btnPickLocation.setOnClickListener(v -> openLocationPicker());
        }
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> save());
        }
    }

    // ── Location picker ───────────────────────────────────────────────────────

    /**
     * Opens LocationPickerActivity for result.
     * Pre-fills the initial map position with the currently selected
     * coordinates (if any) so the ranger sees their last choice.
     */
    private void openLocationPicker() {
        Intent intent = new Intent(this, LocationPickerActivity.class);
        if (selectedLat != 0.0 || selectedLng != 0.0) {
            intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LAT, selectedLat);
            intent.putExtra(LocationPickerActivity.EXTRA_INITIAL_LNG, selectedLng);
        }
        locationPickerLauncher.launch(intent);
    }

    /**
     * Refreshes the coordinate display label below the "Pick on Map" button.
     * Shows "📍 lat, lng" when a location is selected, or a "No location
     * selected" hint otherwise.
     */
    private void updateLocationLabel() {
        if (tvLocationLabel == null) return;
        tvLocationLabel.setVisibility(View.VISIBLE);
        if (selectedLat != 0.0 || selectedLng != 0.0) {
            tvLocationLabel.setText(
                    String.format("📍 %.6f, %.6f", selectedLat, selectedLng)
            );
        } else {
            tvLocationLabel.setText("No location selected");
        }
    }

    // ── Load existing record ──────────────────────────────────────────────────

    private void loadExistingRecord() {
        repository.loadHealthObservation(authorId, existingId, record -> {
            existingRecord = record;
            if (record == null) return;

            MaterialToolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) toolbar.setTitle(R.string.edit_health_observation);

            etTitle.setText(record.title);
            etNotes.setText(record.notes);

            selectedLat = record.lat;
            selectedLng = record.lng;
            updateLocationLabel();
        });
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void save() {
        String title = valueOf(etTitle);
        String notes = valueOf(etNotes);

        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(notes)) {
            Toast.makeText(this,
                    "Please add a title or health notes before saving.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable button to prevent double-submission
        if (btnSave != null) btnSave.setEnabled(false);

        long timestamp = (existingRecord != null)
                ? existingRecord.timestamp
                : System.currentTimeMillis();

        repository.saveHealthObservation(
                authorId,
                (existingRecord != null) ? existingRecord.localId : null,
                title,
                notes,
                timestamp,
                selectedLat,
                selectedLng,
                record -> {
                    // Enqueue a WorkManager upload (CONNECTED + battery-not-low gated)
                    SyncScheduler.enqueueSync(HealthObservationEditorActivity.this);
                    setResult(RESULT_OK);
                    finish();
                }
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String valueOf(TextInputEditText editText) {
        if (editText == null || editText.getText() == null) return "";
        return editText.getText().toString().trim();
    }
}
