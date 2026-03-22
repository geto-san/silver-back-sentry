package com.sbs.ui;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.sbs.R;
import android.content.Intent;
import com.sbs.databinding.ActivityWelcomeBinding;

public class WelcomeActivity extends AppCompatActivity {
    private ActivityWelcomeBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

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