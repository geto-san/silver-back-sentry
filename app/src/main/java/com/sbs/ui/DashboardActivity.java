package com.sbs.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.sbs.SessionManager;
import com.sbs.databinding.ActivityDashboardBinding;

/**
 * DashboardActivity — the main "home screen" shown after a successful login.
 *
 * Changes from the original:
 *   1. Added an "Identify Image" button navigating to ImageClassifierActivity.
 *   2. All original logout logic is preserved unchanged.
 *
 * Layout (activity_dashboard.xml) must now include:
 *   • btnLogout        (existing Button)
 *   • btnIdentifyImage (NEW Button — add this to your XML)
 */
public class DashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        ActivityDashboardBinding binding;
        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // ── 1. Logout button (unchanged) ──────────────────────────────────────
        // Clears the session flag and sends the user back to LoginActivity,
        // removing all activities above it from the back-stack.
        binding.btnLogout.setOnClickListener(v -> {
            SessionManager sessionManager = new SessionManager(this);
            sessionManager.logout();

            Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
            // FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK ensures the user
            // cannot press Back after logout to return to the Dashboard.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        // ── 2. Identify Image button (NEW) ────────────────────────────────────
        // Opens ImageClassifierActivity where the user can choose or photograph
        // an image and have the on-device ML Kit model label its contents.
        binding.btnIdentifyImage.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, ImageClassifierActivity.class)));
    }
}
