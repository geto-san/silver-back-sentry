package com.sbs.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.sbs.R;
import com.sbs.data.SightingRecord;
import com.sbs.data.SightingStore;
import com.sbs.data.SightingSyncManager;

public class SightingEditorActivity extends AppCompatActivity {

    public static final String EXTRA_LAT = "extra_lat";
    public static final String EXTRA_LNG = "extra_lng";
    public static final String EXTRA_SIGHTING_ID = "extra_sighting_id";

    private TextInputEditText etTitle;
    private TextInputEditText etLatitude;
    private TextInputEditText etLongitude;
    private TextInputEditText etNotes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sighting_editor);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etTitle = findViewById(R.id.etSightingTitle);
        etLatitude = findViewById(R.id.etLatitude);
        etLongitude = findViewById(R.id.etLongitude);
        etNotes = findViewById(R.id.etNotes);
        MaterialButton btnSave = findViewById(R.id.btnSaveSighting);

        if (getIntent().hasExtra(EXTRA_LAT) && getIntent().hasExtra(EXTRA_LNG)) {
            double lat = getIntent().getDoubleExtra(EXTRA_LAT, 0.0);
            double lng = getIntent().getDoubleExtra(EXTRA_LNG, 0.0);
            etLatitude.setText(String.valueOf(lat));
            etLongitude.setText(String.valueOf(lng));
        }

        btnSave.setOnClickListener(v -> saveSighting());
    }

    private void saveSighting() {
        String title = valueOf(etTitle);
        String latText = valueOf(etLatitude);
        String lngText = valueOf(etLongitude);
        String notes = valueOf(etNotes);

        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(latText) || TextUtils.isEmpty(lngText)) {
            Toast.makeText(this, "Title, latitude, and longitude are required", Toast.LENGTH_SHORT).show();
            return;
        }

        double lat;
        double lng;
        try {
            lat = Double.parseDouble(latText);
            lng = Double.parseDouble(lngText);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Latitude/longitude must be valid numbers", Toast.LENGTH_SHORT).show();
            return;
        }

        String authorId = SightingSyncManager.resolveAuthorId();
        String authorName = SightingSyncManager.resolveAuthorName();
        SightingRecord record = SightingStore.createSighting(
                this,
                title,
                notes,
                lat,
                lng,
                System.currentTimeMillis(),
                authorId,
                authorName
        );

        if (SightingSyncManager.isOnline(this)) {
            SightingSyncManager.syncSighting(this, record);
        }

        Intent result = new Intent();
        result.putExtra(EXTRA_SIGHTING_ID, record.localId);
        setResult(RESULT_OK, result);
        Toast.makeText(this, "Sighting saved locally", Toast.LENGTH_SHORT).show();
        finish();
    }

    private String valueOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}
