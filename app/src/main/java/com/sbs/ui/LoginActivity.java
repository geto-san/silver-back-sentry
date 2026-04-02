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
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sbs.R;
import com.sbs.data.AppRepository;
import com.sbs.data.RealtimeSyncManager;
import com.sbs.data.SyncScheduler;
import com.sbs.databinding.ActivityLoginBinding;
import com.sbs.notifications.FcmTokenManager;

public class LoginActivity extends BaseActivity {
    private ActivityLoginBinding binding;
    private FirebaseAuth auth;
    private GoogleSignInClient googleSignInClient;
    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getData() == null) {
                    Toast.makeText(this, "Google sign-in cancelled.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    if (account != null) {
                        firebaseAuthWithGoogle(account);
                    }
                } catch (ApiException e) {
                    Toast.makeText(this, "Google sign-in failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });

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
        binding.btnGoogleSignIn.setOnClickListener(v -> startGoogleSignIn());

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
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
                        AppRepository.getInstance(this).upsertCurrentRanger();
                        SyncScheduler.scheduleConfiguredSync(this);
                        RealtimeSyncManager.getInstance(this).start();
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
                            upsertUserProfile(user);
                        }
                        AppRepository.getInstance(this).upsertCurrentRanger();
                        SyncScheduler.scheduleConfiguredSync(this);
                        RealtimeSyncManager.getInstance(this).start();
                        FcmTokenManager.syncCurrentToken(this);
                        Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(this, "Google sign-in failed. Please try again.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void upsertUserProfile(FirebaseUser user) {
        String fullName = user.getDisplayName() != null ? user.getDisplayName() : "";
        String email = user.getEmail() != null ? user.getEmail() : "";
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("fullName", fullName);
        data.put("email", email);
        data.put("updatedAt", System.currentTimeMillis());

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .set(data, com.google.firebase.firestore.SetOptions.merge());
    }
}
