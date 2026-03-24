package com.sbs.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sbs.databinding.ActivitySignUpBinding;
import com.sbs.notifications.FcmTokenManager;

import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends BaseActivity {
    private ActivitySignUpBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySignUpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyWindowInsets(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnSignUp.setOnClickListener(v -> attemptSignUp());
        binding.tvBackToLogin.setOnClickListener(v -> finish());
    }

    private void attemptSignUp() {
        String fullName = valueOf(binding.etFullName);
        String email = valueOf(binding.etEmail);
        String password = valueOf(binding.etPassword);

        if (TextUtils.isEmpty(fullName)) {
            binding.etFullName.setError("Full name is required");
            binding.etFullName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)) {
            binding.etEmail.setError("Email is required");
            binding.etEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.setError("Enter a valid email");
            binding.etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            binding.etPassword.setError("Password is required");
            binding.etPassword.requestFocus();
            return;
        }

        if (password.length() < 6) {
            binding.etPassword.setError("Password must be at least 6 characters");
            binding.etPassword.requestFocus();
            return;
        }

        binding.btnSignUp.setEnabled(false);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        if (auth.getCurrentUser() == null) {
                            binding.btnSignUp.setEnabled(true);
                            Toast.makeText(this, "Sign up failed. Please try again.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        String userId = auth.getCurrentUser().getUid();
                        UserProfileChangeRequest profileUpdate = new UserProfileChangeRequest.Builder()
                                .setDisplayName(fullName)
                                .build();

                        auth.getCurrentUser().updateProfile(profileUpdate)
                                .addOnCompleteListener(updateTask -> saveUserToFirestore(userId, fullName, email));
                    } else {
                        binding.btnSignUp.setEnabled(true);
                        Exception e = task.getException();
                        Toast.makeText(this, mapSignUpError(e), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserToFirestore(String userId, String fullName, String email) {
        Map<String, Object> user = new HashMap<>();
        user.put("fullName", fullName);
        user.put("email", email);
        user.put("createdAt", System.currentTimeMillis());

        db.collection("users").document(userId)
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Account created successfully", Toast.LENGTH_SHORT).show();
                    FcmTokenManager.syncCurrentToken(this);
                    navigateToDashboard();
                })
                .addOnFailureListener(e -> {
                    binding.btnSignUp.setEnabled(true);
                    Toast.makeText(this, "Failed to save user data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void navigateToDashboard() {
        Intent intent = new Intent(SignUpActivity.this, DashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String mapSignUpError(Exception e) {
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return "Password should be at least 6 characters.";
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return "An account already exists for this email.";
        }
        return "Sign up failed. Please try again.";
    }
}
