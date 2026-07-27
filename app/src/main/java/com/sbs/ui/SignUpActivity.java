package com.sbs.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Toast;

import com.sbs.data.AccountSlotManager;

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

        // Task 1: Only Gmail accounts are supported.
        // We validate the domain client-side; Firebase Auth will reject
        // non-existent accounts when the ranger tries to sign in.
        if (!email.toLowerCase().endsWith("@gmail.com")) {
            binding.etEmail.setError(getString(R.string.error_gmail_only));
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

                        FirebaseUser signedInUser = auth.getCurrentUser();
                        if (signedInUser == null) {
                            binding.btnSignUp.setEnabled(true);
                            Toast.makeText(this, "Sign up failed. Please try again.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        String userId = signedInUser.getUid();
                        UserProfileChangeRequest profileUpdate = new UserProfileChangeRequest.Builder()
                                .setDisplayName(fullName)
                                .build();

                        signedInUser.updateProfile(profileUpdate)
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
                    // Task 1 / Task 9: Register this user in the correct account slot
                    // immediately after Firestore write so the drawer chip row is
                    // populated the very first time the Dashboard opens.
                    FirebaseUser newUser = auth.getCurrentUser();
                    if (newUser != null) {
                        AccountSlotManager.getInstance(this).registerCurrentUser(newUser);
                    }
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
                        // Task 9: null-guard before any user operations
                        if (user == null) {
                            Toast.makeText(this, "Google sign-up failed. Please try again.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        // Task 1 / Task 9: Persist profile to Firestore and register slot
                        saveGoogleUserToFirestore(user);
                        // Task 1: Register in AccountSlotManager so the drawer
                        // header shows this account immediately after sign-up.
                        AccountSlotManager.getInstance(this).registerCurrentUser(user);
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
        // Task 9: extra null-guard — user should never be null here but be defensive
        if (user == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("fullName", user.getDisplayName() != null ? user.getDisplayName() : "");
        data.put("email",    user.getEmail()        != null ? user.getEmail()        : "");
        data.put("createdAt", System.currentTimeMillis());

        // SetOptions.merge() ensures we never overwrite existing fields (e.g. createdAt)
        // if the user already has a Firestore profile from a previous sign-in.
        db.collection("users").document(user.getUid())
                .set(data, com.google.firebase.firestore.SetOptions.merge());
    }
}
