package com.sbs.utils;

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

import androidx.annotation.DrawableRes;

import com.sbs.R;

public class NetworkStatusMonitor {

    public interface Listener {
        void onStatusChanged(String label, @DrawableRes int backgroundRes);
    }

    private final Context appContext;
    private final ConnectivityManager connectivityManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ConnectivityManager.NetworkCallback networkCallback;
    private BroadcastReceiver airplaneReceiver;
    private Listener listener;
    private boolean started = false;

    public NetworkStatusMonitor(Context context) {
        appContext = context.getApplicationContext();
        connectivityManager =
                (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public void start(Listener listener) {
        this.listener = listener;
        if (started || connectivityManager == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                dispatch();
            }

            @Override
            public void onLost(Network network) {
                dispatch();
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                dispatch();
            }
        };

        airplaneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                dispatch();
            }
        };

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        connectivityManager.registerNetworkCallback(request, networkCallback);
        
        IntentFilter filter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        appContext.registerReceiver(airplaneReceiver, filter);

        started = true;
        dispatch();
    }

    public void stop() {
        if (!started) return;

        try {
            if (networkCallback != null && connectivityManager != null) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            }
        } catch (Exception ignored) {
        }

        try {
            if (airplaneReceiver != null) {
                appContext.unregisterReceiver(airplaneReceiver);
            }
        } catch (Exception ignored) {
        }

        started = false;
    }

    private void dispatch() {
        if (listener == null || connectivityManager == null) return;

        boolean airplaneModeOn = isAirplaneModeOn();
        Network activeNetwork = connectivityManager.getActiveNetwork();
        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(activeNetwork);

        final String label;
        final int backgroundRes;

        if (airplaneModeOn) {
            label = "Airplane mode on";
            backgroundRes = R.drawable.bg_network_warning;
        } else if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            label = "Online";
            backgroundRes = R.drawable.bg_network_online;
        } else if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            label = "Connected - no internet access";
            backgroundRes = R.drawable.bg_network_warning;
        } else {
            label = "Offline";
            backgroundRes = R.drawable.bg_network_offline;
        }

        mainHandler.post(() -> listener.onStatusChanged(label, backgroundRes));
    }

    private boolean isAirplaneModeOn() {
        return Settings.Global.getInt(
                appContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON,
                0
        ) == 1;
    }
}
