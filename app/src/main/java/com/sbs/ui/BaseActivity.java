package com.sbs.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.sbs.NetworkStatusMonitor;
import com.sbs.R;

public abstract class BaseActivity extends AppCompatActivity implements NetworkStatusMonitor.NetworkStatusListener {

    protected NetworkStatusMonitor networkStatusMonitor;
    protected TextView tvNetworkStatus;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        networkStatusMonitor = new NetworkStatusMonitor(this, this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        setupNetworkStatusLabel();
        networkStatusMonitor.startMonitoring();
    }

    @Override
    protected void onStop() {
        super.onStop();
        networkStatusMonitor.stopMonitoring();
    }

    private void setupNetworkStatusLabel() {
        tvNetworkStatus = findViewById(R.id.tvNetworkStatus);
    }

    @Override
    public void onStatusChanged(boolean isOnline, boolean isAirplaneModeOn) {
        if (tvNetworkStatus == null) return;

        if (isAirplaneModeOn) {
            tvNetworkStatus.setText(R.string.network_airplane_mode);
            tvNetworkStatus.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark));
            tvNetworkStatus.setVisibility(View.VISIBLE);
        } else if (!isOnline) {
            tvNetworkStatus.setText(R.string.network_offline);
            tvNetworkStatus.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            tvNetworkStatus.setVisibility(View.VISIBLE);
        } else {
            tvNetworkStatus.setText(R.string.network_online);
            tvNetworkStatus.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            tvNetworkStatus.setVisibility(View.VISIBLE);
        }
    }

    protected void applyWindowInsets(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    protected String valueOf(EditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}