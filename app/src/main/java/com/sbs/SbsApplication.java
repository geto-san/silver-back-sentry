package com.sbs.app;

import android.app.Application;

import com.sbs.data.AppRepository;
import com.sbs.data.RealtimeSyncManager;
import com.sbs.data.SyncScheduler;
import com.sbs.notifications.AppNotificationHelper;

public final class SbsApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppRepository.getInstance(this);
        AppNotificationHelper.ensureChannel(this);
        SyncScheduler.startConnectivityMonitoring(this);
        SyncScheduler.scheduleConfiguredSync(this);
        RealtimeSyncManager.getInstance(this).start();
    }
}
