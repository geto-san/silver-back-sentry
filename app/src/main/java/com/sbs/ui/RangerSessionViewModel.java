package com.sbs.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.sbs.data.RangerSessionManager;

public final class RangerSessionViewModel extends AndroidViewModel {

    private final RangerSessionManager sessionManager;
    private final MutableLiveData<String> activeRangerId = new MutableLiveData<>();

    public RangerSessionViewModel(@NonNull Application application) {
        super(application);
        sessionManager = new RangerSessionManager(application);
        activeRangerId.setValue(sessionManager.getActiveRangerId());
    }

    public MutableLiveData<String> getActiveRangerId() {
        return activeRangerId;
    }

    public void setActiveRangerId(String rangerId) {
        sessionManager.setActiveRangerId(rangerId);
        activeRangerId.setValue(rangerId);
    }
}
