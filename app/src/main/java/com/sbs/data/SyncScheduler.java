package com.sbs.data;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.sbs.data.work.FetchRemoteSightingsWorker;
import com.sbs.data.work.UploadPendingDataWorker;

import java.util.concurrent.TimeUnit;

public final class SyncScheduler {

    private static final String ONE_TIME_SYNC = "one_time_sync";
    private static volatile boolean monitoringStarted;

    private SyncScheduler() {
    }

    public static void enqueueSync(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        OneTimeWorkRequest upload = new OneTimeWorkRequest.Builder(UploadPendingDataWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();

        OneTimeWorkRequest refresh = new OneTimeWorkRequest.Builder(FetchRemoteSightingsWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();

        WorkManager.getInstance(context)
                .beginUniqueWork(ONE_TIME_SYNC, ExistingWorkPolicy.KEEP, upload)
                .then(refresh)
                .enqueue();
    }

    public static void scheduleConfiguredSync(Context context) {
        enqueueSync(context);
    }

    public static void startConnectivityMonitoring(Context context) {
        if (monitoringStarted) {
            return;
        }
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        if (manager == null) {
            return;
        }
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        manager.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                enqueueSync(context);
            }
        });
        monitoringStarted = true;
    }
}
