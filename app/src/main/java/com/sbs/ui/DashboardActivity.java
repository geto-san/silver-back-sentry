package com.sbs.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.sbs.SessionManager;
import com.sbs.databinding.ActivityDashboardBinding;

public class DashboardActivity extends AppCompatActivity {
    private ActivityDashboardBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Handle Status Bar / Navigation Bar insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Apply top padding to the main content's toolbar or container
            binding.mainContent.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            // Apply padding to the side panel as well to avoid overlap with status bar
            binding.sidePanel.setPadding(binding.sidePanel.getPaddingLeft(), systemBars.top, binding.sidePanel.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        setupClickListeners();

        // Handle back press to close drawer if open
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void setupClickListeners() {
        binding.btnMenuToggle.setOnClickListener(v -> openMenu());
        binding.btnMenuClose.setOnClickListener(v -> hideMenu());

        binding.menuNewSighting.setOnClickListener(v -> showMenuToast("New Sighting screen not implemented yet"));
        binding.menuHealthObservation.setOnClickListener(v -> showMenuToast("Health Observation screen not implemented yet"));
        binding.menuSyncStatus.setOnClickListener(v -> showMenuToast("Sync Status screen not implemented yet"));
        binding.menuSettings.setOnClickListener(v -> showMenuToast("Settings screen not implemented yet"));

        binding.tvLogout.setOnClickListener(v -> {
            SessionManager sessionManager = new SessionManager(this);
            sessionManager.logout();

            Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        binding.btnIdentifyImage.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, ImageClassifierActivity.class)));
    }

    private void showMenuToast(String message) {
        hideMenu();
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void openMenu() {
        binding.drawerLayout.openDrawer(GravityCompat.START);
    }

    private void hideMenu() {
        binding.drawerLayout.closeDrawer(GravityCompat.START);
    }
}
