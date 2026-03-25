package com.sbs.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.sbs.R;
import com.sbs.data.AppSettingsManager;
import com.sbs.data.PatrolLogRecord;
import com.sbs.data.PatrolLogStore;
import com.sbs.data.SightingSyncManager;

import java.util.List;

public class PatrolLogsActivity extends BaseActivity implements PatrolLogsAdapter.LogActionListener {

    private PatrolLogsAdapter adapter;
    private View emptyState;
    private AppSettingsManager appSettingsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patrol_logs);
        applyWindowInsets(findViewById(R.id.toolbar).getRootView());

        appSettingsManager = new AppSettingsManager(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.recyclerPatrolLogs);
        emptyState = findViewById(R.id.tvEmptyState);
        adapter = new PatrolLogsAdapter(appSettingsManager, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadLogs();
    }

    private void loadLogs() {
        List<PatrolLogRecord> records = PatrolLogStore.getAll(this);
        adapter.submitList(records);
        emptyState.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onSyncNow(PatrolLogRecord record) {
        if (SightingSyncManager.isOnline(this)) {
            SightingSyncManager.syncPatrolLog(this, record);
            loadLogs();
            Toast.makeText(this, "Syncing...", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDelete(PatrolLogRecord record) {
        Toast.makeText(this, "Delete not implemented", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEdit(PatrolLogRecord record) {
        Toast.makeText(this, "Edit not implemented", Toast.LENGTH_SHORT).show();
    }
}
