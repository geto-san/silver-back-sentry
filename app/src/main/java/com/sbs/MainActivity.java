package com.sbs;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.sbs.ui.WelcomeActivity;
import com.sbs.ui.DashboardActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean hasUser = FirebaseAuth.getInstance().getCurrentUser() != null;

        Intent intent;
        if (hasUser) {
            intent = new Intent(this, DashboardActivity.class);
        } else {
            intent = new Intent(this, WelcomeActivity.class);
        }

        startActivity(intent);
        finish();
    }
}
