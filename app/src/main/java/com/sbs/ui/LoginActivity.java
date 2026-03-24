package com.sbs.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.sbs.databinding.ActivityLoginBinding;
import com.sbs.notifications.FcmTokenManager;

public class LoginActivity extends BaseActivity {
    private ActivityLoginBinding binding;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyWindowInsets(binding.getRoot());

        auth = FirebaseAuth.getInstance();

        binding.btnGetStarted.setOnClickListener(v -> attemptLogin());
        binding.tvCreateAccount.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, SignUpActivity.class)));
    }

    private void attemptLogin() {
        String email = valueOf(binding.etUsername);
        String password = valueOf(binding.etPassword);

        if (TextUtils.isEmpty(email)) {
            binding.etUsername.setError("Email is required");
            binding.etUsername.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etUsername.setError("Enter a valid email");
            binding.etUsername.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            binding.etPassword.setError("Password is required");
            binding.etPassword.requestFocus();
            return;
        }

        binding.btnGetStarted.setEnabled(false);

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    binding.btnGetStarted.setEnabled(true);

                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show();
                        FcmTokenManager.syncCurrentToken(this);
                        Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Exception e = task.getException();
                        Toast.makeText(this, mapLoginError(e), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private String mapLoginError(Exception e) {
        if (e instanceof FirebaseAuthInvalidUserException) {
            return "No account found for this email.";
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return "Invalid email or password.";
        }
        return "Login failed. Please try again.";
    }
}
