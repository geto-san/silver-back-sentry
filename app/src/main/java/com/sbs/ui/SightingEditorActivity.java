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
import com.sbs.data.SightingRecord;
import com.sbs.data.SyncScheduler;

/**
 * SightingEditorActivity
 *
 * Create or edit a gorilla sighting record.
 *
 * Architecture (from the devised Java architecture):
 *   - Writes go to AppRepository (which writes to Room, synced=PENDING).
 *   - Room LiveData fires automatically in SightingsActivity/MapActivity.
 *   - WorkManager upload is enqueued after save (battery + network gated).
 *
 * Feature 4 — Location picking:
 *   The "Pick on Map" button launches LocationPickerActivity for result.
 *   On confirm, lat/lng are stored in selectedLat / selectedLng and shown
 *   in the coordinator label below the button.
 *
 * Feature 6 — Material Design:
 *   TextInputLayout / TextInputEditText for all text fields.
 *   Outlined buttons for media stubs, filled MaterialButton for save.
 */
public class SightingEditorActivity extends BaseActivity {

    // ── Views ─────────────────────────────────────────────────────────────────

    private TextInputEditText etTitle;
    private TextInputEditText etNotes;
    private TextInputEditText etRadius;
    private TextView          tvLocationLabel;
    private MaterialButton    btnPickLocation;
    private MaterialButton    btnSave;

    // ── State ─────────────────────────────────────────────────────────────────

    private double selectedLat = 0.0;
    private double selectedLng = 0.0;

    private String existingSightingId;
    private SightingRecord existingRecord;

    private AppRepository repository;
    private String authorId;

    // ── Activity result launchers ─────────────────────────────────────────────

    /**
     * Launcher for LocationPickerActivity.
     * On RESULT_OK, reads lat/lng from the returned Intent and updates
     * selectedLat / selectedLng, then refreshes the coordinate label.
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

    /**
     * Photo capture stub launcher.
     * Replace the body with a real CameraX / camera-intent implementation
     * when photo capture is needed.
     */
    private final ActivityResultLauncher<Intent> photoLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        // TODO: handle captured photo URI, store path in existingRecord
                        Toast.makeText(this, "Photo capture coming soon", Toast.LENGTH_SHORT).show();
                    }
            );

    /**
     * Video capture stub launcher.
     */
    private final ActivityResultLauncher<Intent> videoLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        // TODO: handle captured video URI
                        Toast.makeText(this, "Video capture coming soon", Toast.LENGTH_SHORT).show();
                    }
            );

    /**
     * Audio recording stub launcher.
     */
    private final ActivityResultLauncher<Intent> audioLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        // TODO: handle audio recording result
                        Toast.makeText(this, "Audio recording coming soon", Toast.LENGTH_SHORT).show();
                    }
            );

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sighting_editor);
        applyWindowInsets(findViewById(R.id.toolbar).getRootView());

        repository = AppRepository.getInstance(this);
        authorId   = FirebaseAuth.getInstance().getUid();

        if (authorId == null) {
            Toast.makeText(this, "You must be signed in to log sightings.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews();
        setupToolbar();
        setupClickListeners();

        // ── Load existing record if editing ──────────────────────────────────
        existingSightingId = getIntent().getStringExtra("sighting_id");
        if (existingSightingId != null) {
            loadExistingRecord();
        } else {
            // Pre-fill coordinates if the caller (e.g. MapActivity) passed them
            selectedLat = getIntent().getDoubleExtra("lat", 0.0);
            selectedLng = getIntent().getDoubleExtra("lng", 0.0);
            updateLocationLabel();
        }
    }

    // ── View binding ──────────────────────────────────────────────────────────

    private void bindViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etTitle        = findViewById(R.id.etSightingTitle);
        etNotes        = findViewById(R.id.etNotes);
        etRadius       = findViewById(R.id.etRadius);
        tvLocationLabel = findViewById(R.id.tvLocationLabel);
        btnPickLocation = findViewById(R.id.btnPickLocation);
        btnSave         = findViewById(R.id.btnSaveSighting);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    // ── Click listeners ───────────────────────────────────────────────────────

    private void setupClickListeners() {
        // ── Location picker ───────────────────────────────────────────────────
        btnPickLocation.setOnClickListener(v -> openLocationPicker());

        // ── Media stubs ───────────────────────────────────────────────────────
        // These are intentional stubs. Wire up CameraX or ACTION_IMAGE_CAPTURE
        // intents here when the feature is built out.
        findViewById(R.id.btnCapturePhoto).setOnClickListener(v ->
                Toast.makeText(this, "Photo capture — coming soon", Toast.LENGTH_SHORT).show()
        );
        findViewById(R.id.btnRecordVideo).setOnClickListener(v ->
                Toast.makeText(this, "Video capture — coming soon", Toast.LENGTH_SHORT).show()
        );
        findViewById(R.id.btnRecordAudio).setOnClickListener(v ->
                Toast.makeText(this, "Audio recording — coming soon", Toast.LENGTH_SHORT).show()
        );

        // ── Save ──────────────────────────────────────────────────────────────
        btnSave.setOnClickListener(v -> saveSighting());
    }

    // ── Location picker ───────────────────────────────────────────────────────

    /**
     * Opens LocationPickerActivity for result.
     * If coordinates are already selected, passes them in as the initial
     * camera / pin position so the ranger sees their previous choice.
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
     * Updates the coordinate label below the "Pick on Map" button.
     * Shows "Not set" if neither coordinate is non-zero.
     */
    private void updateLocationLabel() {
        if (tvLocationLabel == null) return;
        if (selectedLat != 0.0 || selectedLng != 0.0) {
            tvLocationLabel.setText(
                    String.format("📍 %.6f, %.6f", selectedLat, selectedLng)
            );
            tvLocationLabel.setVisibility(View.VISIBLE);
        } else {
            tvLocationLabel.setText("No location selected");
            tvLocationLabel.setVisibility(View.VISIBLE);
        }
    }

    // ── Load existing record ──────────────────────────────────────────────────

    private void loadExistingRecord() {
        repository.loadSighting(authorId, existingSightingId, record -> {
            existingRecord = record;
            if (record == null) return;

            MaterialToolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) toolbar.setTitle(R.string.edit_sighting);

            etTitle.setText(record.title);
            etNotes.setText(record.notes);
            etRadius.setText(String.valueOf(record.radius));

            selectedLat = record.lat;
            selectedLng = record.lng;
            updateLocationLabel();
        });
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void saveSighting() {
        String title      = valueOf(etTitle);
        String notes      = valueOf(etNotes);
        String radiusText = valueOf(etRadius);

        // Require at least a title or notes so empty records aren't created
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(notes)) {
            Toast.makeText(this,
                    "Please provide at least a title or description.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        float radius = 0f;
        try {
            if (!TextUtils.isEmpty(radiusText)) {
                radius = Float.parseFloat(radiusText);
            }
        } catch (NumberFormatException ignored) {
            // Leave radius at 0 if the field contains non-numeric text
        }

        // Disable button to prevent double-submission
        btnSave.setEnabled(false);

        long timestamp = (existingRecord != null)
                ? existingRecord.timestamp
                : System.currentTimeMillis();

        String audioPath = (existingRecord != null) ? existingRecord.audioPath : null;
        String imagePath = (existingRecord != null) ? existingRecord.imagePath : null;
        String videoPath = (existingRecord != null) ? existingRecord.videoPath : null;

        repository.saveSighting(
                authorId,
                (existingRecord != null) ? existingRecord.localId : null,
                title,
                notes,
                selectedLat,
                selectedLng,
                timestamp,
                radius,
                audioPath,
                imagePath,
                videoPath,
                record -> {
                    // Enqueue a WorkManager sync (constraints: CONNECTED + battery OK)
                    SyncScheduler.enqueueSync(SightingEditorActivity.this);
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
