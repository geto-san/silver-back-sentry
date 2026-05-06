package com.sbs.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.sbs.data.AppRepository;
import com.sbs.data.HealthObservationRecord;

import java.util.List;

public class HealthObservationsViewModel extends AndroidViewModel {

    private final AppRepository repository;
    private final String currentUserId;

    public HealthObservationsViewModel(@NonNull Application application) {
        super(application);
        repository = AppRepository.getInstance(application);
        currentUserId = FirebaseAuth.getInstance().getUid();
    }

    public LiveData<List<HealthObservationRecord>> getHealthObservations() {
        if (currentUserId == null) return null;
        return repository.observeHealthObservations(currentUserId);
    }

    public void deleteHealthObservation(String localId) {
        if (currentUserId != null) {
            repository.deleteHealthObservation(currentUserId, localId);
        }
    }

    public String getCurrentUserId() {
        return currentUserId;
    }
}
