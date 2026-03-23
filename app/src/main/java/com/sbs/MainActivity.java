package com.sbs;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.sbs.ui.WelcomeActivity;
import com.sbs.ui.DashboardActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager sessionManager = new SessionManager(this);

        Intent intent;
        if (!sessionManager.hasOpenedBefore()) {
            sessionManager.setHasOpenedBefore(true);
            intent = new Intent(this, WelcomeActivity.class);
        } else if (sessionManager.isLoggedIn()) {
            intent = new Intent(this, DashboardActivity.class);
        } else {
            intent = new Intent(this, WelcomeActivity.class);
        }

        startActivity(intent);
        finish();
    }
}
