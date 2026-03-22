package com.sbs.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.sbs.SessionManager;
import com.sbs.databinding.ActivityDashboardBinding;

public class DashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        ActivityDashboardBinding binding;

        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.menuNewSighting.setOnClickListener(v ->
                Toast.makeText(this, "New Sighting screen not implemented yet", Toast.LENGTH_SHORT).show());

        binding.menuHealthObservation.setOnClickListener(v ->
                Toast.makeText(this, "Health Observation screen not implemented yet", Toast.LENGTH_SHORT).show());

        binding.menuViewRecords.setOnClickListener(v ->
                Toast.makeText(this, "View Records screen not implemented yet", Toast.LENGTH_SHORT).show());

        binding.menuSyncStatus.setOnClickListener(v ->
                Toast.makeText(this, "Sync Status screen not implemented yet", Toast.LENGTH_SHORT).show());

        binding.menuPatrolLog.setOnClickListener(v ->
                Toast.makeText(this, "Patrol Log screen not implemented yet", Toast.LENGTH_SHORT).show());

        binding.menuActivityTimeline.setOnClickListener(v ->
                Toast.makeText(this, "Activity Timeline screen not implemented yet", Toast.LENGTH_SHORT).show());

        binding.menuProfile.setOnClickListener(v ->
                Toast.makeText(this, "Profile screen not implemented yet", Toast.LENGTH_SHORT).show());

        binding.menuTools.setOnClickListener(v ->
                Toast.makeText(this, "Tools screen not implemented yet", Toast.LENGTH_SHORT).show());

        binding.menuSettings.setOnClickListener(v ->
                Toast.makeText(this, "Settings screen not implemented yet", Toast.LENGTH_SHORT).show());

        binding.tvLogout.setOnClickListener(v -> {
            SessionManager sessionManager = new SessionManager(this);
            sessionManager.logout();

            Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}