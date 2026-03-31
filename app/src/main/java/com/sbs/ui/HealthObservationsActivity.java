package com.sbs.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.sbs.R;
import com.sbs.data.AppRepository;
import com.sbs.data.HealthObservationRecord;

public final class HealthObservationsActivity extends BaseActivity implements HealthObservationsAdapter.HealthActionListener {

    private final ActivityResultLauncher<Intent> editorLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            });

    private AppRepository repository;
    private HealthObservationsAdapter adapter;
    private View emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_observations);
        applyWindowInsets(findViewById(R.id.toolbar).getRootView());

        repository = AppRepository.getInstance(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        emptyState = findViewById(R.id.tvEmptyState);
        adapter = new HealthObservationsAdapter(this);
        androidx.recyclerview.widget.RecyclerView recyclerView = findViewById(R.id.recyclerHealthObservations);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        findViewById(R.id.fabAddHealthObservation).setOnClickListener(v ->
                editorLauncher.launch(new Intent(this, HealthObservationEditorActivity.class)));

        repository.observeHealthObservations().observe(this, records -> {
            adapter.submitList(records);
            emptyState.setVisibility(records == null || records.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onOpen(HealthObservationRecord record) {
        Intent intent = new Intent(this, RecordDetailActivity.class);
        intent.putExtra("record_id", record.localId);
        intent.putExtra("record_type", "HEALTH_OBSERVATION");
        startActivity(intent);
    }

    @Override
    public void onEdit(HealthObservationRecord record) {
        Intent intent = new Intent(this, HealthObservationEditorActivity.class);
        intent.putExtra("health_id", record.localId);
        editorLauncher.launch(intent);
    }

    @Override
    public void onDelete(HealthObservationRecord record) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete)
                .setMessage("Delete this health observation locally?")
                .setPositiveButton(R.string.delete, (dialog, which) -> repository.deleteHealthObservation(record.localId))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
