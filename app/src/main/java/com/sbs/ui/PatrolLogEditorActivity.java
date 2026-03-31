package com.sbs.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.sbs.R;
import com.sbs.data.AppRepository;
import com.sbs.data.PatrolLogRecord;
import com.sbs.data.SyncScheduler;

public class PatrolLogEditorActivity extends BaseActivity {

    private TextInputEditText etTitle;
    private TextInputEditText etNotes;
    private String existingLogId;
    private PatrolLogRecord existingRecord;
    private AppRepository repository;
    private String authorId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patrol_log_editor);
        applyWindowInsets(findViewById(R.id.toolbar).getRootView());

        repository = AppRepository.getInstance(this);
        authorId = FirebaseAuth.getInstance().getUid();
        if (authorId == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etTitle = findViewById(R.id.etLogTitle);
        etNotes = findViewById(R.id.etLogNotes);
        MaterialButton btnSave = findViewById(R.id.btnSaveLog);

        existingLogId = getIntent().getStringExtra("log_id");
        if (existingLogId != null) {
            repository.loadPatrolLog(existingLogId, record -> {
                existingRecord = record;
                if (record == null) {
                    return;
                }
                toolbar.setTitle("Edit Patrol Log");
                etTitle.setText(record.title);
                etNotes.setText(record.notes);
            });
        }

        btnSave.setOnClickListener(v -> saveLog());
        
        findViewById(R.id.btnAttachAudio).setOnClickListener(v -> Toast.makeText(this, "Audio attachment not implemented", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnAttachVideo).setOnClickListener(v -> Toast.makeText(this, "Video attachment not implemented", Toast.LENGTH_SHORT).show());
    }

    private void saveLog() {
        String title = valueOf(etTitle);
        String notes = valueOf(etNotes);

        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, "Please provide a title", Toast.LENGTH_SHORT).show();
            return;
        }

        long timestamp = existingRecord != null ? existingRecord.timestamp : System.currentTimeMillis();
        repository.savePatrolLog(
                authorId,
                existingRecord != null ? existingRecord.localId : null,
                title,
                notes,
                timestamp,
                existingRecord != null ? existingRecord.audioPath : null,
                existingRecord != null ? existingRecord.videoPath : null,
                record -> {
                    SyncScheduler.enqueueSync(this);
                    setResult(RESULT_OK);
                    finish();
                }
        );
    }

    private String valueOf(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }
}
