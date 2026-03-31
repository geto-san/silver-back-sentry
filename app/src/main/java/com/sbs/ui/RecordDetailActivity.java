package com.sbs.ui;

import android.os.Bundle;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.sbs.R;
import com.sbs.data.AppRepository;
import com.sbs.data.HealthObservationRecord;
import com.sbs.data.PatrolLogRecord;
import com.sbs.data.RecordType;
import com.sbs.data.SightingRecord;

import java.text.DateFormat;
import java.util.Date;

public final class RecordDetailActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_detail);
        applyWindowInsets(findViewById(R.id.toolbar).getRootView());

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        String recordId = getIntent().getStringExtra("record_id");
        String recordType = getIntent().getStringExtra("record_type");
        if (recordId == null || recordType == null) {
            finish();
            return;
        }

        AppRepository repository = AppRepository.getInstance(this);
        if (RecordType.SIGHTING.equals(recordType)) {
            repository.loadSighting(recordId, this::bindSighting);
        } else if (RecordType.PATROL_LOG.equals(recordType)) {
            repository.loadPatrolLog(recordId, this::bindPatrolLog);
        } else {
            repository.loadHealthObservation(recordId, this::bindHealthObservation);
        }
    }

    private void bindSighting(SightingRecord record) {
        if (record == null) return;
        bind(record.title, "Sighting", record.authorName, record.timestamp, record.notes + "\n\nLat " + record.lat + ", Lng " + record.lng);
    }

    private void bindPatrolLog(PatrolLogRecord record) {
        if (record == null) return;
        bind(record.title, "Patrol Log", record.authorName, record.timestamp, record.notes);
    }

    private void bindHealthObservation(HealthObservationRecord record) {
        if (record == null) return;
        bind(record.title, "Health Observation", record.authorName, record.timestamp, record.notes + "\n\nLat " + record.lat + ", Lng " + record.lng);
    }

    private void bind(String title, String type, String author, long timestamp, String notes) {
        ((TextView) findViewById(R.id.tvDetailType)).setText(type);
        ((TextView) findViewById(R.id.tvDetailTitle)).setText(title);
        ((TextView) findViewById(R.id.tvDetailMeta)).setText("By " + author + " • " + DateFormat.getDateTimeInstance().format(new Date(timestamp)));
        ((TextView) findViewById(R.id.tvDetailNotes)).setText(notes);
    }
}
