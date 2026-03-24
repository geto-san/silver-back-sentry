package com.sbs.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.sbs.R;
import com.sbs.data.SightingRecord;
import com.sbs.data.SightingStore;
import com.sbs.data.SightingSyncManager;

import java.util.List;

public class SightingsActivity extends AppCompatActivity {

    private SightingsAdapter adapter;
    private View emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sightings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.recyclerSightings);
        emptyState = findViewById(R.id.tvEmptyState);
        adapter = new SightingsAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadSightings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SightingSyncManager.isOnline(this)) {
            SightingSyncManager.syncPendingSightings(this);
        }
        loadSightings();
    }

    private void loadSightings() {
        List<SightingRecord> records = SightingStore.getAll(this);
        adapter.submitList(records);
        emptyState.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
