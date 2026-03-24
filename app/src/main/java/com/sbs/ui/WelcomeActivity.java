package com.sbs.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.google.firebase.auth.FirebaseAuth;
import com.sbs.databinding.ActivityWelcomeBinding;

public class WelcomeActivity extends BaseActivity {
    private ActivityWelcomeBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if user is already logged in
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            startActivity(new Intent(WelcomeActivity.this, DashboardActivity.class));
            finish();
            return;
        }

        binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyWindowInsets(binding.getRoot());

        showInitialState();

        binding.btnGetStarted.setOnClickListener(v -> showAuthOptions());

        binding.btnLogin.setOnClickListener(v ->
                startActivity(new Intent(WelcomeActivity.this, LoginActivity.class)));

        binding.btnSignUp.setOnClickListener(v ->
                startActivity(new Intent(WelcomeActivity.this, SignUpActivity.class)));
    }

    private void showInitialState() {
        binding.btnGetStarted.setVisibility(View.VISIBLE);
        binding.layoutAuthOptions.setVisibility(View.GONE);
    }

    private void showAuthOptions() {
        binding.btnGetStarted.setVisibility(View.GONE);
        binding.layoutAuthOptions.setVisibility(View.VISIBLE);
    }
}
