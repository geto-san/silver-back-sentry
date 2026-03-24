package com.sbs.ui;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.sbs.R;
import com.sbs.data.FieldDataStore;

public class HealthObservationActivity extends AppCompatActivity {

    private TextInputEditText etSubject;
    private TextInputEditText etSeverity;
    private TextInputEditText etFindings;
    private TextInputEditText etActionTaken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_observation);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        etSubject = findViewById(R.id.etSubject);
        etSeverity = findViewById(R.id.etSeverity);
        etFindings = findViewById(R.id.etFindings);
        etActionTaken = findViewById(R.id.etActionTaken);
        MaterialButton btnSave = findViewById(R.id.btnSaveObservation);

        toolbar.setNavigationOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveObservation());
    }

    private void saveObservation() {
        String subject = valueOf(etSubject);
        String severity = valueOf(etSeverity);
        String findings = valueOf(etFindings);
        String actionTaken = valueOf(etActionTaken);

        if (subject.isEmpty() || severity.isEmpty() || findings.isEmpty()) {
            Toast.makeText(this, "Subject, severity, and findings are required", Toast.LENGTH_SHORT).show();
            return;
        }

        FieldDataStore.saveHealthObservation(this, subject, severity, findings, actionTaken);
        saveObservationToFirestore(subject, severity, findings, actionTaken);
        Toast.makeText(this, "Health observation saved locally", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void saveObservationToFirestore(
            String subject,
            String severity,
            String findings,
            String actionTaken
    ) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String authorId = user != null ? user.getUid() : null;
        String authorName = user != null ? user.getDisplayName() : null;
        String authorEmail = user != null ? user.getEmail() : null;

        if (authorName == null && authorEmail != null) {
            int atIndex = authorEmail.indexOf('@');
            authorName = atIndex > 0 ? authorEmail.substring(0, atIndex) : authorEmail;
        }

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("subject", subject);
        data.put("severity", severity);
        data.put("findings", findings);
        data.put("actionTaken", actionTaken);
        data.put("timestamp", System.currentTimeMillis());
        data.put("authorId", authorId);
        data.put("authorName", authorName);
        data.put("createdAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance()
                .collection("health_observations")
                .add(data);
    }

    private String valueOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}
