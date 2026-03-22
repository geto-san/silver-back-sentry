package com.sbs;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import android.content.SharedPreferences;
import android.content.Intent;
import com.sbs.ui.WelcomeActivity;
import com.sbs.ui.LoginActivity;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "silverback_prefs";
    private static final String KEY_HAS_OPENED_BEFORE = "has_opened_before";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        SessionManager sessionManager = new SessionManager(this);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean hasOpenedBefore = prefs.getBoolean(KEY_HAS_OPENED_BEFORE, false);

        Intent intent;
        if (!hasOpenedBefore) {
            sessionManager.setHasOpenedBefore(true);
            prefs.edit().putBoolean(KEY_HAS_OPENED_BEFORE, true).apply();
            intent = new Intent(this, WelcomeActivity.class);
        } else if (sessionManager.isLoggedIn()) {
            intent = new Intent(this, LoginActivity.class);
        } else {
            intent = new Intent(this, LoginActivity.class);
        }

        startActivity(intent);
        finish();
    }
}