package com.sbs.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.sbs.R;
import com.sbs.data.AppRepository;
import com.sbs.data.SightingRecord;

public class SightingsActivity extends BaseActivity implements SightingsAdapter.SightingActionListener {

    private SightingsAdapter adapter;
    private View emptyState;
    private AppRepository repository;
    private String currentUserId;

    private final ActivityResultLauncher<Intent> editorLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sightings);
        applyWindowInsets(findViewById(R.id.toolbar).getRootView());

        repository = AppRepository.getInstance(this);
        currentUserId = FirebaseAuth.getInstance().getUid();
        if (currentUserId == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.recyclerSightings);
        emptyState = findViewById(R.id.tvEmptyState);
        adapter = new SightingsAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        FloatingActionButton fab = findViewById(R.id.fabAddSighting);
        fab.setOnClickListener(v -> editorLauncher.launch(new Intent(this, SightingEditorActivity.class)));

        repository.observeSightings().observe(this, records -> {
            adapter.submitList(records);
            emptyState.setVisibility(records == null || records.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onOpen(SightingRecord record) {
        Intent intent = new Intent(this, RecordDetailActivity.class);
        intent.putExtra("record_id", record.localId);
        intent.putExtra("record_type", "SIGHTING");
        startActivity(intent);
    }

    @Override
    public void onDelete(SightingRecord record) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Sighting")
                .setMessage("Are you sure you want to delete this sighting locally?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    repository.deleteSighting(record.localId);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onEdit(SightingRecord record) {
        Intent intent = new Intent(this, SightingEditorActivity.class);
        intent.putExtra("sighting_id", record.localId);
        editorLauncher.launch(intent);
    }
}
