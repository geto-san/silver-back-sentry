package com.sbs.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sbs.R;
import com.sbs.data.AppRepository;
import com.sbs.data.RealtimeSyncManager;
import com.sbs.data.SyncScheduler;
import com.sbs.databinding.ActivitySignUpBinding;
import com.sbs.notifications.FcmTokenManager;

import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends BaseActivity {
    private ActivitySignUpBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private GoogleSignInClient googleSignInClient;
    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getData() == null) {
                    Toast.makeText(this, "Google sign-up cancelled.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    if (account != null) {
                        firebaseAuthWithGoogle(account);
                    }
                } catch (ApiException e) {
                    Toast.makeText(this, "Google sign-up failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });

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
        binding.btnGoogleSignUp.setOnClickListener(v -> startGoogleSignIn());

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
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
                    AppRepository.getInstance(this).upsertCurrentRanger();
                    SyncScheduler.scheduleConfiguredSync(this);
                    RealtimeSyncManager.getInstance(this).start();
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

    private void startGoogleSignIn() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        googleLauncher.launch(signInIntent);
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        auth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            saveGoogleUserToFirestore(user);
                        }
                        AppRepository.getInstance(this).upsertCurrentRanger();
                        SyncScheduler.scheduleConfiguredSync(this);
                        RealtimeSyncManager.getInstance(this).start();
                        FcmTokenManager.syncCurrentToken(this);
                        navigateToDashboard();
                    } else {
                        Toast.makeText(this, "Google sign-up failed. Please try again.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveGoogleUserToFirestore(FirebaseUser user) {
        Map<String, Object> data = new HashMap<>();
        data.put("fullName", user.getDisplayName() != null ? user.getDisplayName() : "");
        data.put("email", user.getEmail() != null ? user.getEmail() : "");
        data.put("createdAt", System.currentTimeMillis());

        db.collection("users").document(user.getUid())
                .set(data, com.google.firebase.firestore.SetOptions.merge());
    }
}
