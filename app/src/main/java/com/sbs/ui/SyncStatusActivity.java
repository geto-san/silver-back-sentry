package com.sbs.ui;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.sbs.R;
import com.sbs.data.FieldDataStore;
import com.sbs.data.SightingStore;

public class SyncStatusActivity extends AppCompatActivity {

    private TextView tvPendingTotal;
    private TextView tvSightingsCount;
    private TextView tvHealthCount;
    private TextView tvLastSync;
    private TextView tvNetworkStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_status);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvPendingTotal = findViewById(R.id.tvPendingTotal);
        tvSightingsCount = findViewById(R.id.tvSightingsCount);
        tvHealthCount = findViewById(R.id.tvHealthCount);
        tvLastSync = findViewById(R.id.tvLastSync);
        tvNetworkStatus = findViewById(R.id.tvNetworkStatus);

        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        tvPendingTotal.setText(String.valueOf(FieldDataStore.getPendingItemCount(this)));
        tvSightingsCount.setText(String.valueOf(SightingStore.getTotalCount(this)));
        tvHealthCount.setText(String.valueOf(FieldDataStore.getHealthObservationCount(this)));
        tvLastSync.setText(FieldDataStore.getLastSync(this));
        tvNetworkStatus.setText(isOnline() ? "Online" : "Offline");
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }

        Network network = cm.getActiveNetwork();
        if (network == null) {
            return false;
        }

        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && (
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        );
    }
}
