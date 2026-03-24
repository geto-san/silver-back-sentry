package com.sbs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.NonNull;

public class NetworkStatusMonitor {

    public interface NetworkStatusListener {
        void onStatusChanged(boolean isOnline, boolean isAirplaneModeOn);
    }

    private final Context context;
    private final ConnectivityManager connectivityManager;
    private final NetworkStatusListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            notifyStatusChanged();
        }

        @Override
        public void onLost(@NonNull Network network) {
            notifyStatusChanged();
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            notifyStatusChanged();
        }
    };

    private final BroadcastReceiver airplaneModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(intent.getAction())) {
                notifyStatusChanged();
            }
        }
    };

    public NetworkStatusMonitor(Context context, NetworkStatusListener listener) {
        this.context = context.getApplicationContext();
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.listener = listener;
    }

    public void startMonitoring() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        connectivityManager.registerNetworkCallback(request, networkCallback);
        
        IntentFilter filter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        context.registerReceiver(airplaneModeReceiver, filter);
        
        notifyStatusChanged(); // Initial check
    }

    public void stopMonitoring() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            context.unregisterReceiver(airplaneModeReceiver);
        } catch (Exception e) {
            // Callback or receiver might not be registered
        }
    }

    public boolean isOnline() {
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) return false;
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public boolean isAirplaneModeOn() {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    private void notifyStatusChanged() {
        final boolean isOnline = isOnline();
        final boolean isAirplaneModeOn = isAirplaneModeOn();
        mainHandler.post(() -> listener.onStatusChanged(isOnline, isAirplaneModeOn));
    }
}
