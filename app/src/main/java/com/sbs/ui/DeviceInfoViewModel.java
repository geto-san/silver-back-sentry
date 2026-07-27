package com.sbs.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.sbs.hardware.HardwareState;
import com.sbs.hardware.HardwareStatusLiveData;

/**
 * DeviceInfoViewModel
 *
 * Owns the {@link HardwareStatusLiveData} instance so that it survives
 * screen rotation.  The Activity simply observes {@link #getHardwareState()}
 * and re-renders the UI whenever a new {@link HardwareState} snapshot arrives.
 *
 * This follows the same pattern used throughout the app:
 *   LiveData (from custom LiveData subclass)
 *       → AndroidViewModel (survives config change)
 *           → Activity (renders only, no business logic)
 *
 * The HardwareStatusLiveData registers its BroadcastReceiver in onActive()
 * (when the first observer subscribes) and unregisters in onInactive()
 * (when the last observer disappears).  Because the ViewModel holds the
 * LiveData reference across rotations, the receiver is never leaked and is
 * only active while the screen is visible.
 */
public final class DeviceInfoViewModel extends AndroidViewModel {

    private final HardwareStatusLiveData hardwareStatusLiveData;

    public DeviceInfoViewModel(@NonNull Application application) {
        super(application);
        // HardwareStatusLiveData is created once here and reused across
        // configuration changes.  Its BroadcastReceiver lifecycle is managed
        // automatically by LiveData's onActive / onInactive hooks.
        hardwareStatusLiveData = new HardwareStatusLiveData(application);
    }

    /**
     * Returns the live stream of {@link HardwareState} snapshots.
     *
     * Each emission represents a fresh reading of all hardware components:
     * GPS, camera, microphone, cellular radio, battery level, airplane mode,
     * and available storage.  A new snapshot is emitted whenever any of these
     * change (battery intent, provider-change broadcast, airplane-mode broadcast).
     *
     * Observe this in the Activity:
     * <pre>
     *   viewModel.getHardwareState().observe(this, state -> {
     *       tvBattery.setText(state.batteryPercentLabel());
     *       // ... update other rows
     *   });
     * </pre>
     */
    public LiveData<HardwareState> getHardwareState() {
        return hardwareStatusLiveData;
    }
}
