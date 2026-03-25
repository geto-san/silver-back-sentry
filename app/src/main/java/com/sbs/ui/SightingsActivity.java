package com.sbs.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.sbs.R;
import com.sbs.data.AppSettingsManager;
import com.sbs.data.SightingRecord;
import com.sbs.data.SightingStore;
import com.sbs.data.SightingSyncManager;

import java.util.List;

public class SightingsActivity extends BaseActivity implements SightingsAdapter.SightingActionListener {

    private SightingsAdapter adapter;
    private View emptyState;
    private AppSettingsManager appSettingsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sightings);
        applyWindowInsets(findViewById(R.id.toolbar).getRootView());

        appSettingsManager = new AppSettingsManager(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.recyclerSightings);
        emptyState = findViewById(R.id.tvEmptyState);
        adapter = new SightingsAdapter(appSettingsManager, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadSightings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appSettingsManager.isAutoSyncEnabled() && SightingSyncManager.isOnline(this)) {
            SightingSyncManager.syncAllPending(this);
        }
        loadSightings();
    }

    private void loadSightings() {
        // In a real implementation, this would fetch from Firestore and merge with Local
        List<SightingRecord> records = SightingStore.getAll(this);
        adapter.submitList(records);
        emptyState.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onSyncNow(SightingRecord record) {
        if (SightingSyncManager.isOnline(this)) {
            SightingSyncManager.syncSighting(this, record);
            loadSightings();
            Toast.makeText(this, "Syncing...", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDelete(SightingRecord record) {
        // Implement delete logic (Local and Firestore if synced)
        Toast.makeText(this, "Delete not implemented yet", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEdit(SightingRecord record) {
        Intent intent = new Intent(this, SightingEditorActivity.class);
        intent.putExtra("sighting_id", record.localId);
        startActivity(intent);
    }
}
