package com.sbs.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.sbs.SessionManager;
import com.sbs.databinding.ActivitySignUpBinding;

public class SignUpActivity extends AppCompatActivity {
    private ActivitySignUpBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        binding = ActivitySignUpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnCreateAccount.setOnClickListener(v -> {
            String username = binding.etUsername.getText().toString().trim();
            String password = binding.etPassword.getText().toString().trim();

            if (username.isEmpty()) {
                binding.etUsername.setError("Enter username");
                binding.etUsername.requestFocus();
                return;
            }

            if (password.isEmpty()) {
                binding.etPassword.setError("Enter password");
                binding.etPassword.requestFocus();
                return;
            }

            SessionManager sessionManager = new SessionManager(this);
            sessionManager.setLoggedIn(true);

            Intent intent = new Intent(SignUpActivity.this, DashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        binding.tvBackToLogin.setOnClickListener(v -> finish());

//        binding.tvNeedHelp.setOnClickListener(v ->
//                Toast.makeText(this, "Help may not get a filler arc, yet!", Toast.LENGTH_SHORT).show());
    }
}