package com.sbs.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.sbs.data.AppRepository;
import com.sbs.data.SightingRecord;

import java.util.List;

public final class SightingsViewModel extends AndroidViewModel {

    private final AppRepository repository;
    private final String currentUserId;
    private final LiveData<List<SightingRecord>> sightings;

    public SightingsViewModel(@NonNull Application application) {
        super(application);
        repository = AppRepository.getInstance(application);
        currentUserId = FirebaseAuth.getInstance().getUid();
        sightings = currentUserId != null
                ? repository.observeSightings(currentUserId)
                : new androidx.lifecycle.MutableLiveData<>(java.util.Collections.emptyList());
    }

    public LiveData<List<SightingRecord>> getSightings() {
        return sightings;
    }

    public String getCurrentUserId() {
        return currentUserId;
    }

    public void deleteSighting(String localId) {
        if (currentUserId != null) {
            repository.deleteSighting(currentUserId, localId);
        }
    }
}
