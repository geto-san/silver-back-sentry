package com.sbs.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.sbs.SessionManager;
import android.widget.Toast;
import com.sbs.databinding.ActivityLoginBinding;

public class LoginActivity extends AppCompatActivity {
    private ActivityLoginBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnGetStarted.setOnClickListener(v -> {
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

            startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
            finish();
        });

        binding.tvCreateAccount.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, SignUpActivity.class)));

        binding.tvNeedHelp.setOnClickListener(v ->
                Toast.makeText(this, "Help flow currently on a filler arc! \uD83D\uDE11", Toast.LENGTH_SHORT).show());
    }
}