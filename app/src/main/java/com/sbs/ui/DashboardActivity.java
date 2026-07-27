package com.sbs.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.messaging.FirebaseMessaging;
import com.sbs.R;
import com.sbs.data.AccountProfile;
import com.sbs.data.AccountSlotManager;
import com.sbs.data.AppRepository;
import com.sbs.data.AppSettingsManager;
import com.sbs.data.RecordType;
import com.sbs.data.RealtimeSyncManager;
import com.sbs.data.SightingRecord;
import com.sbs.databinding.ActivityDashboardBinding;
import com.sbs.notifications.FcmTokenManager;

import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.location.LocationComponent;
import org.maplibre.android.location.LocationComponentActivationOptions;
import org.maplibre.android.location.modes.CameraMode;
import org.maplibre.android.location.modes.RenderMode;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;

import java.util.ArrayList;
import java.util.List;

public class DashboardActivity extends BaseActivity implements OnMapReadyCallback {

    // ── Tag ───────────────────────────────────────────────────────────────────
    private static final String TAG = "DashboardActivity";

    // ── Map style URLs ────────────────────────────────────────────────────────

    /**
     * Task 4: OpenFreeMap Liberty — completely free, no API key, Google-Maps-like
     * vector tile style. Hosted by openfreemap.org with no usage limits.
     */
    private static final String STREET_STYLE_URL =
            "https://tiles.openfreemap.org/styles/liberty";

    /**
     * Task 4: Satellite style — Esri World Imagery raster tiles (free, no API key)
     * with Esri Reference overlay for labels. Inline JSON builds the MapLibre style.
     */
    private static final String SATELLITE_STYLE_JSON =
            "{\"version\":8," +
            "\"sources\":{" +
            "\"sat\":{\"type\":\"raster\"," +
            "\"tiles\":[\"https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}\"]," +
            "\"tileSize\":256," +
            "\"attribution\":\"Tiles &copy; Esri &mdash; Earthstar Geographics\"}," +
            "\"lbl\":{\"type\":\"raster\"," +
            "\"tiles\":[\"https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}\"]," +
            "\"tileSize\":256}}," +
            "\"layers\":[" +
            "{\"id\":\"satellite\",\"type\":\"raster\",\"source\":\"sat\"}," +
            "{\"id\":\"labels\",\"type\":\"raster\",\"source\":\"lbl\"}" +
            "]}";

    // ── Saved-state keys ──────────────────────────────────────────────────────
    private static final String PREFS_DASHBOARD_STATE = "sbs_dashboard_state";
    private static final String KEY_MAP_LAT           = "map_lat";
    private static final String KEY_MAP_LNG           = "map_lng";
    private static final String KEY_MAP_ZOOM          = "map_zoom";
    private static final String KEY_IS_SATELLITE      = "is_satellite";

    // ── View binding / map ────────────────────────────────────────────────────
    private ActivityDashboardBinding binding;
    private MapLibreMap mapLibreMap;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean restoredMapState  = false;
    private boolean actionMenuOpen    = false;
    private boolean isSatelliteMode   = false;
    private final List<org.maplibre.android.annotations.Marker> savedSightingMarkers =
            new ArrayList<>();

    // ── Data / services ───────────────────────────────────────────────────────
    private AppRepository      repository;
    private AppSettingsManager appSettingsManager;
    private AccountSlotManager accountSlotManager;

    // ── Activity-result launchers ─────────────────────────────────────────────

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean any = false;
                        for (Boolean g : result.values()) { if (Boolean.TRUE.equals(g)) { any = true; break; } }
                        if (any) enableMyLocation();
                        else Toast.makeText(this,
                                "Location permission is required for map features",
                                Toast.LENGTH_SHORT).show();
                    });

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (!Boolean.TRUE.equals(granted))
                            Toast.makeText(this,
                                    "Notifications are disabled. You won't receive alerts.",
                                    Toast.LENGTH_SHORT).show();
                    });

    private final ActivityResultLauncher<Intent> recordEditorLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> { /* data arrives via LiveData */ });

    // ══════════════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MapLibre.getInstance(this);

        appSettingsManager = new AppSettingsManager(this);
        repository         = AppRepository.getInstance(this);
        appSettingsManager.applyTheme();

        super.onCreate(savedInstanceState);

        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.mapView.onCreate(savedInstanceState);
        binding.mapView.getMapAsync(this);

        // ── AccountSlotManager ────────────────────────────────────────────────
        accountSlotManager = AccountSlotManager.getInstance(this);
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current != null) accountSlotManager.registerCurrentUser(current);

        // ── Edge-to-edge insets ───────────────────────────────────────────────
        int ol = binding.sidePanel.getPaddingLeft();
        int ot = binding.sidePanel.getPaddingTop();
        int or_ = binding.sidePanel.getPaddingRight();
        int ob = binding.sidePanel.getPaddingBottom();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
                binding.drawerLayout, (v, insets) -> {
                    androidx.core.graphics.Insets sb =
                            insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                    binding.mainContent.setPadding(sb.left, sb.top, sb.right, 0);
                    binding.sidePanel.setPadding(ol, ot + sb.top, or_, ob + sb.bottom);
                    return insets;
                });

        // Restore satellite preference
        isSatelliteMode = getSharedPreferences(PREFS_DASHBOARD_STATE, MODE_PRIVATE)
                .getBoolean(KEY_IS_SATELLITE, false);

        // ── Service startup ───────────────────────────────────────────────────
        FcmTokenManager.syncCurrentToken(this);
        repository.upsertCurrentRanger();
        RealtimeSyncManager.getInstance(this).start();

        refreshAccountSwitcherUI();
        requestNotificationPermissionIfNeeded();
        logFcmToken();
        handleIncomingAlert(getIntent());
        setupClickListeners();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Map
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void onMapReady(@NonNull MapLibreMap map) {
        this.mapLibreMap = map;

        // Task 4: Use OpenFreeMap Liberty (Google Maps-like feel, no API key)
        // or Esri satellite depending on the user's last preference.
        String styleSource = isSatelliteMode ? null : STREET_STYLE_URL;
        Style.Builder builder = isSatelliteMode
                ? new Style.Builder().fromJson(SATELLITE_STYLE_JSON)
                : new Style.Builder().fromUri(STREET_STYLE_URL);

        map.setStyle(builder, style -> {
            updateMapStyleLabel();
            setupMyLocationOverlay();
            observeSightings();
            restoreDashboardState();
            if (!restoredMapState && appSettingsManager.isAutoCenterMapEnabled()) {
                centerMapOnUser();
            }
        });
    }

    /**
     * Task 4: Toggles between OpenFreeMap Liberty (street) and
     * Esri World Imagery (satellite) views.
     */
    private void toggleMapStyle() {
        if (mapLibreMap == null) return;
        isSatelliteMode = !isSatelliteMode;

        // Persist preference
        getSharedPreferences(PREFS_DASHBOARD_STATE, MODE_PRIVATE)
                .edit().putBoolean(KEY_IS_SATELLITE, isSatelliteMode).apply();

        Style.Builder builder = isSatelliteMode
                ? new Style.Builder().fromJson(SATELLITE_STYLE_JSON)
                : new Style.Builder().fromUri(STREET_STYLE_URL);

        mapLibreMap.setStyle(builder, style -> {
            updateMapStyleLabel();
            if (hasLocationPermissions()) enableMyLocation();
            // Re-plot sighting markers after style reload
            renderStoredSightings();
            String uid = FirebaseAuth.getInstance().getUid();
            if (uid != null) {
                repository.observeSightings(uid).observe(this, records -> {
                    renderStoredSightings();
                    if (records != null) for (SightingRecord r : records) addSightingMarker(r);
                });
            }
        });
    }

    /** Updates the satellite/street toggle chip label and icon hint. */
    private void updateMapStyleLabel() {
        if (binding.tvMapStyleLabel == null) return;
        // Label shows what mode you will SWITCH TO (opposite of current)
        binding.tvMapStyleLabel.setText(
                isSatelliteMode
                        ? getString(R.string.map_street_view)
                        : getString(R.string.map_satellite_view));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Click listeners
    // ══════════════════════════════════════════════════════════════════════════

    private void setupClickListeners() {

        // ── Drawer ────────────────────────────────────────────────────────────
        binding.btnMenuToggle.setOnClickListener(v -> openMenu());
        binding.btnMenuClose.setOnClickListener(v -> hideMenu());

        // ── Toolbar ───────────────────────────────────────────────────────────
        binding.btnNotifications.setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));
        binding.btnThemeMode.setOnClickListener(v -> showThemePicker());

        // ── Map style chip ────────────────────────────────────────────────────
        binding.btnMapStyle.setOnClickListener(v -> toggleMapStyle());

        // ── Scrim (closes action menu when tapped) ────────────────────────────
        binding.actionMenuScrim.setOnClickListener(v -> closeActionMenu());

        // Task 8: "My Location" mini FAB — directly centres map on GPS position
        // (no popup; the "Centre on My Location" option was removed from the
        //  "New Record" popup in Task 8).
        binding.btnMyLocation.setOnClickListener(v -> centerMapOnUser());

        // Task 7 & 8: "New Record" main FAB — opens the redesigned 3-option popup
        binding.btnNewRecord.setOnClickListener(v -> toggleActionMenu());

        // ── Popup: New Sighting ───────────────────────────────────────────────
        binding.actionSighting.setOnClickListener(v -> {
            closeActionMenu();
            LatLng loc = resolveCurrentLocation();
            if (loc != null) openSightingEditor(loc);
            else             openSightingEditor(new LatLng(0, 0));
        });

        // ── Popup: New Patrol Log (Task 7 — newly added) ──────────────────────
        binding.actionPatrolLog.setOnClickListener(v -> {
            closeActionMenu();
            hideMenu();
            startActivity(new Intent(this, PatrolLogEditorActivity.class));
        });

        // ── Popup: New Health Observation (Task 7 — newly added) ─────────────
        binding.actionHealthObservation.setOnClickListener(v -> {
            closeActionMenu();
            hideMenu();
            startActivity(new Intent(this, HealthObservationEditorActivity.class));
        });

        // ── Drawer menu items ─────────────────────────────────────────────────
        binding.menuNewSighting.setOnClickListener(v -> {
            hideMenu();
            startActivity(new Intent(this, SightingsActivity.class));
        });
        binding.menuHealthObservations.setOnClickListener(v -> {
            hideMenu();
            startActivity(new Intent(this, HealthObservationsActivity.class));
        });
        binding.menuPatrolLogs.setOnClickListener(v -> {
            hideMenu();
            startActivity(new Intent(this, PatrolLogsActivity.class));
        });

        binding.menuDeviceInfo.setOnClickListener(v -> {
            hideMenu();
            startActivity(new Intent(this, DeviceInfoActivity.class));
        });

        // ── Account chip: Slot 1 ──────────────────────────────────────────────
        binding.accountChip1.setOnClickListener(v -> {
            if (accountSlotManager.getActiveSlot() == 1) {
                AccountProfile p = accountSlotManager.getSlot(1);
                Toast.makeText(this,
                        (p != null ? p.resolvedName() : "Account 1") + " is already active",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            AccountProfile slot1 = accountSlotManager.getSlot(1);
            if (slot1 != null) confirmAndSwitch(slot1);
        });
        binding.accountChip1.setOnLongClickListener(v -> {
            if (accountSlotManager.getActiveSlot() == 1) {
                Toast.makeText(this,
                        getString(R.string.account_cannot_remove_active),
                        Toast.LENGTH_SHORT).show();
                return true;
            }
            AccountProfile s1 = accountSlotManager.getSlot(1);
            if (s1 != null) showRemoveAccountDialog(s1, 1);
            return true;
        });

        // ── Account chip: Slot 2 ──────────────────────────────────────────────
        binding.accountChip2.setOnClickListener(v -> {
            if (accountSlotManager.getActiveSlot() == 2) {
                AccountProfile p = accountSlotManager.getSlot(2);
                Toast.makeText(this,
                        (p != null ? p.resolvedName() : "Account 2") + " is already active",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            AccountProfile slot2 = accountSlotManager.getSlot(2);
            if (slot2 != null) confirmAndSwitch(slot2);
        });
        binding.accountChip2.setOnLongClickListener(v -> {
            if (accountSlotManager.getActiveSlot() == 2) {
                Toast.makeText(this,
                        getString(R.string.account_cannot_remove_active),
                        Toast.LENGTH_SHORT).show();
                return true;
            }
            AccountProfile s2 = accountSlotManager.getSlot(2);
            if (s2 != null) showRemoveAccountDialog(s2, 2);
            return true;
        });

        // ── Add account chip ──────────────────────────────────────────────────
        binding.accountChipAdd.setOnClickListener(v -> {
            if (accountSlotManager.isAtMaxCapacity()) {
                Toast.makeText(this,
                        getString(R.string.account_at_capacity),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra(LoginActivity.EXTRA_MODE, LoginActivity.MODE_ADD_SECOND);
            hideMenu();
            startActivity(intent);
        });

        // Task 3: Logout with 2-account awareness
        binding.tvLogout.setOnClickListener(v -> handleLogout());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Task 3 — Smart Logout (2-account awareness)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Task 3: When the ranger taps "Log Out":
     *  - If TWO accounts are registered: removes the active slot and switches
     *    to the other account (that slot becomes the sole active slot, and one
     *    slot becomes free for a new account).
     *  - If ONE account is registered: full sign-out, navigate to WelcomeActivity.
     */
    private void handleLogout() {
        AccountProfile activeProfile = accountSlotManager.getActiveProfile();
        int            activeSlot   = accountSlotManager.getActiveSlot();
        int            otherSlot    = (activeSlot == 1) ? 2 : 1;
        AccountProfile otherProfile = accountSlotManager.getSlot(otherSlot);

        String activeName = activeProfile != null ? activeProfile.resolvedName() : "current account";

        if (otherProfile != null) {
            // Two accounts — remove current, switch to the other
            String otherName = otherProfile.resolvedName();
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.logout_title))
                    .setMessage(getString(R.string.logout_message_switch, activeName, otherName))
                    .setPositiveButton(getString(R.string.logout_confirm), (dialog, which) -> {
                        // Step 1: Remove the active slot (frees one slot)
                        accountSlotManager.clearSlot(activeSlot);
                        // Step 2: Sign out of Firebase (active session ends)
                        FirebaseAuth.getInstance().signOut();
                        // Step 3: Switch to the remaining account silently
                        switchToAccount(otherProfile);
                    })
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show();
        } else {
            // Single account — full sign-out
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.logout_title))
                    .setMessage(getString(R.string.logout_message_single))
                    .setPositiveButton(getString(R.string.logout_confirm), (dialog, which) -> {
                        FirebaseAuth.getInstance().signOut();
                        // Do NOT clear slots on logout — profiles survive restarts (WhatsApp model)
                        Intent intent = new Intent(this, WelcomeActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Task 2 — Silent account switching
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Task 2: Shows a confirmation dialog before switching.
     * Password is NOT required for Google accounts — a silent re-authentication
     * attempt is made first using GoogleSignIn.silentSignIn() with the target
     * account's email. Only if that fails does the app fall back to the full
     * LoginActivity flow.
     */
    private void confirmAndSwitch(AccountProfile target) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.account_switch_title))
                .setMessage("Switch to " + target.resolvedName() + "?")
                .setPositiveButton(getString(R.string.account_switch_confirm),
                        (dialog, which) -> switchToAccount(target))
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    /**
     * Task 2: Attempts a silent Google account switch.
     *
     * GoogleSignInOptions.setAccountName(email) instructs the Google Sign-In
     * client to attempt authentication for THAT specific account using cached
     * device credentials — no password prompt shown to the user.
     *
     * On success  → Firebase re-authenticates silently → DashboardActivity
     *               refreshes in place (no navigate-away needed).
     * On failure  → Falls back to LoginActivity(MODE_SWITCH) with the target
     *               email pre-filled so only the password needs to be entered.
     */
    private void switchToAccount(AccountProfile target) {
        String webClientId;
        try {
            webClientId = getString(R.string.default_web_client_id);
        } catch (Exception e) {
            // default_web_client_id is auto-generated from google-services.json;
            // if it's missing the silent path cannot work — go straight to login.
            performFullSwitch(target);
            return;
        }

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .setAccountName(target.email)   // ← request THIS specific Google account
                .build();

        GoogleSignIn.getClient(this, gso)
                .silentSignIn()
                .addOnSuccessListener(googleAccount -> {
                    // Got a fresh Google ID token without asking the user for credentials
                    AuthCredential credential =
                            GoogleAuthProvider.getCredential(googleAccount.getIdToken(), null);
                    FirebaseAuth.getInstance()
                            .signInWithCredential(credential)
                            .addOnSuccessListener(authResult -> onSwitchSuccess(authResult.getUser(), target))
                            .addOnFailureListener(e -> {
                                Log.w(TAG, "Firebase re-auth after silent sign-in failed", e);
                                performFullSwitch(target);
                            });
                })
                .addOnFailureListener(e -> {
                    // Silent sign-in not possible (account not cached, or email/password account)
                    Log.i(TAG, "Silent sign-in unavailable; falling back to full login", e);
                    performFullSwitch(target);
                });
    }

    /**
     * Task 2: Called after a successful silent (or full) Firebase sign-in.
     * Updates the slot manager, refreshes UI, and restarts real-time listeners.
     */
    private void onSwitchSuccess(FirebaseUser user, AccountProfile target) {
        if (user == null) { performFullSwitch(target); return; }

        accountSlotManager.registerCurrentUser(user);
        repository.upsertCurrentRanger();

        // Start Firestore listeners for the new user. RealtimeSyncManager.start() 
        // internally checks if the userId changed and stops old listeners if needed.
        RealtimeSyncManager.getInstance(this).start();

        refreshAccountSwitcherUI();
        observeSightings();           // re-observe sightings for the new ranger
        hideMenu();
        Toast.makeText(this,
                getString(R.string.switch_success, target.resolvedName()),
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Task 2: Fallback when silent sign-in fails.
     * Signs out and opens LoginActivity with the target email pre-filled.
     */
    private void performFullSwitch(AccountProfile target) {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_MODE,          LoginActivity.MODE_SWITCH);
        intent.putExtra(LoginActivity.EXTRA_PREFILL_EMAIL, target.email);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Account Switcher UI
    // ══════════════════════════════════════════════════════════════════════════

    private void refreshAccountSwitcherUI() {
        if (accountSlotManager == null) return;

        int            activeSlot   = accountSlotManager.getActiveSlot();
        AccountProfile slot1        = accountSlotManager.getSlot(1);
        AccountProfile slot2        = accountSlotManager.getSlot(2);
        AccountProfile activeProfile = accountSlotManager.getActiveProfile();
        int            count        = accountSlotManager.getAccountCount();

        // Large header avatar + name + email
        if (activeProfile != null) {
            binding.tvUserName.setText(activeProfile.resolvedName());
            TextView tvEmail = binding.getRoot().findViewById(R.id.tvActiveEmail);
            if (tvEmail != null) tvEmail.setText(activeProfile.email);
            Glide.with(this)
                    .load(activeProfile.photoUrl)
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.drawable.bg_dashboard_avatar)
                    .error(R.drawable.bg_dashboard_avatar)
                    .into(binding.ivUserAvatar);
        } else {
            binding.tvUserName.setText(R.string.test_user_nim);
            binding.ivUserAvatar.setImageResource(R.drawable.bg_dashboard_avatar);
        }

        // Slot chips
        if (slot1 != null) {
            updateChip(binding.chipCard1, binding.ivSlot1, binding.tvSlot1Name, slot1, activeSlot == 1);
            binding.accountChip1.setVisibility(View.VISIBLE);
        } else {
            binding.accountChip1.setVisibility(View.GONE);
        }

        if (slot2 != null) {
            updateChip(binding.chipCard2, binding.ivSlot2, binding.tvSlot2Name, slot2, activeSlot == 2);
            binding.accountChip2.setVisibility(View.VISIBLE);
        } else {
            binding.accountChip2.setVisibility(View.GONE);
        }

        // Add chip — hidden when at max capacity (2 accounts)
        binding.accountChipAdd.setVisibility(
                count < AccountSlotManager.MAX_ACCOUNTS ? View.VISIBLE : View.GONE);
    }

    private void updateChip(MaterialCardView card,
                             ShapeableImageView avatar,
                             TextView label,
                             AccountProfile profile,
                             boolean isActive) {
        int ringColor = isActive
                ? resolveAttrColor(com.google.android.material.R.attr.colorSecondary)
                : getResources().getColor(R.color.sbs_input_stroke, getTheme());
        card.setStrokeColor(ColorStateList.valueOf(ringColor));
        card.setStrokeWidth(dpToPx(isActive ? 3f : 1.5f));

        Glide.with(this)
                .load(profile.photoUrl)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.bg_dashboard_avatar)
                .error(R.drawable.bg_dashboard_avatar)
                .into(avatar);

        if (isActive) {
            label.setText(R.string.account_active_label);
            label.setTypeface(null, Typeface.BOLD);
            label.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorSecondary));
        } else {
            label.setText(profile.shortLabel());
            label.setTypeface(null, Typeface.NORMAL);
            label.setTextColor(resolveAttrColor(R.attr.menuSecondaryTextColor));
        }
    }

    private void showRemoveAccountDialog(AccountProfile profile, int slot) {
        String name = profile.resolvedName();
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.account_remove_title))
                .setMessage(getString(R.string.account_remove_message, name))
                .setPositiveButton(getString(R.string.account_remove_confirm), (d, w) -> {
                    accountSlotManager.clearSlot(slot);
                    refreshAccountSwitcherUI();
                    Toast.makeText(this,
                            getString(R.string.account_removed, name),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Sightings (markers on map)
    // ══════════════════════════════════════════════════════════════════════════

    private void observeSightings() {
        String rangerId = FirebaseAuth.getInstance().getUid();
        if (rangerId == null) return;

        repository.observeSightings(rangerId).observe(this, records -> {
            renderStoredSightings();
            if (records == null) return;
            for (SightingRecord record : records) addSightingMarker(record);
        });

        repository.observeUnreadNotificationCount(rangerId).observe(this,
                count -> binding.viewNotificationDot.setVisibility(
                        count != null && count > 0 ? View.VISIBLE : View.GONE));
    }

    private void renderStoredSightings() {
        if (mapLibreMap == null) return;
        for (org.maplibre.android.annotations.Marker m : savedSightingMarkers) {
            mapLibreMap.removeMarker(m);
        }
        savedSightingMarkers.clear();
    }

    private void addSightingMarker(SightingRecord record) {
        if (mapLibreMap == null) return;
        if (record.lat == 0.0 && record.lng == 0.0) return;

        LatLng point = new LatLng(record.lat, record.lng);
        org.maplibre.android.annotations.Marker marker = mapLibreMap.addMarker(
                new MarkerOptions()
                        .position(point)
                        .title(record.title != null && !record.title.isEmpty() ? record.title : "Sighting")
                        .snippet(record.notes));

        mapLibreMap.setOnMarkerClickListener(m -> {
            if (m.getTitle() != null && m.getTitle().equals(marker.getTitle())
                    && m.getPosition().equals(point)) {
                Intent intent = new Intent(this, RecordDetailActivity.class);
                intent.putExtra("record_id",   record.localId);
                intent.putExtra("record_type", RecordType.SIGHTING);
                startActivity(intent);
                return true;
            }
            return false;
        });

        savedSightingMarkers.add(marker);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Action menu (FAB popup)
    // ══════════════════════════════════════════════════════════════════════════

    private void toggleActionMenu() {
        if (actionMenuOpen) closeActionMenu(); else openActionMenu();
    }

    private void openActionMenu() {
        actionMenuOpen = true;
        binding.actionMenuScrim.setVisibility(View.VISIBLE);
        binding.actionMenuContainer.setVisibility(View.VISIBLE);
        binding.actionMenuContainer.setAlpha(0f);
        binding.actionMenuContainer.animate().alpha(1f).setDuration(180).start();
        // Rotate the "New Record" FAB icon 45° to become an × while the menu is open
        binding.btnNewRecord.animate().rotation(45f).setDuration(180).start();
    }

    private void closeActionMenu() {
        actionMenuOpen = false;
        binding.actionMenuScrim.setVisibility(View.GONE);
        binding.actionMenuContainer.setVisibility(View.GONE);
        binding.btnNewRecord.animate().rotation(0f).setDuration(180).start();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Map / location helpers
    // ══════════════════════════════════════════════════════════════════════════

    private void setupMyLocationOverlay() {
        if (hasLocationPermissions()) enableMyLocation();
        else requestLocationPermissions();
    }

    private void openSightingEditor(LatLng point) {
        Intent intent = new Intent(this, SightingEditorActivity.class);
        if (point.getLatitude() != 0.0 || point.getLongitude() != 0.0) {
            intent.putExtra("lat", point.getLatitude());
            intent.putExtra("lng", point.getLongitude());
        }
        recordEditorLauncher.launch(intent);
    }

    private void saveDashboardState() {
        if (mapLibreMap == null) return;
        org.maplibre.android.camera.CameraPosition pos = mapLibreMap.getCameraPosition();
        getSharedPreferences(PREFS_DASHBOARD_STATE, MODE_PRIVATE).edit()
                .putString(KEY_MAP_LAT,  String.valueOf(pos.target.getLatitude()))
                .putString(KEY_MAP_LNG,  String.valueOf(pos.target.getLongitude()))
                .putString(KEY_MAP_ZOOM, String.valueOf(pos.zoom))
                .putBoolean(KEY_IS_SATELLITE, isSatelliteMode)
                .apply();
    }

    private void restoreDashboardState() {
        if (mapLibreMap == null) return;
        SharedPreferences prefs = getSharedPreferences(PREFS_DASHBOARD_STATE, MODE_PRIVATE);
        String latStr  = prefs.getString(KEY_MAP_LAT, null);
        String lngStr  = prefs.getString(KEY_MAP_LNG, null);
        String zoomStr = prefs.getString(KEY_MAP_ZOOM, null);
        if (latStr != null && lngStr != null) {
            try {
                double lat  = Double.parseDouble(latStr);
                double lng  = Double.parseDouble(lngStr);
                double zoom = zoomStr != null ? Double.parseDouble(zoomStr) : 9.0;
                mapLibreMap.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), zoom));
                restoredMapState = true;
            } catch (Exception ignored) { }
        }
    }

    private void centerMapOnUser() {
        if (!hasLocationPermissions()) { requestLocationPermissions(); return; }
        if (mapLibreMap == null) return;
        LocationComponent lc = mapLibreMap.getLocationComponent();
        if (lc.isLocationComponentActivated() && lc.getLastKnownLocation() != null) {
            android.location.Location last = lc.getLastKnownLocation();
            mapLibreMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                            new LatLng(last.getLatitude(), last.getLongitude()), 16.0),
                    800);
        } else {
            Toast.makeText(this, "Waiting for GPS fix…", Toast.LENGTH_SHORT).show();
        }
    }

    private LatLng resolveCurrentLocation() {
        if (!hasLocationPermissions()) { requestLocationPermissions(); return null; }
        if (mapLibreMap == null || mapLibreMap.getStyle() == null) return null;

        LocationComponent lc = mapLibreMap.getLocationComponent();
        // Check activation state before calling isLocationComponentEnabled()
        // to avoid UnactivatedLocationComponentException.
        if (!lc.isLocationComponentActivated()) {
            enableMyLocation();
        }

        if (lc.isLocationComponentActivated() && lc.isLocationComponentEnabled()) {
            android.location.Location last = lc.getLastKnownLocation();
            return last != null ? new LatLng(last.getLatitude(), last.getLongitude()) : null;
        }
        return null;
    }

    private boolean hasLocationPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissions() {
        locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION});
    }

    @SuppressWarnings("MissingPermission")
    private void enableMyLocation() {
        if (mapLibreMap == null || mapLibreMap.getStyle() == null) return;
        LocationComponent lc = mapLibreMap.getLocationComponent();
        lc.activateLocationComponent(
                LocationComponentActivationOptions.builder(this, mapLibreMap.getStyle()).build());
        lc.setLocationComponentEnabled(true);
        lc.setCameraMode(CameraMode.NONE);
        lc.setRenderMode(RenderMode.COMPASS);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Misc helpers
    // ══════════════════════════════════════════════════════════════════════════

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void logFcmToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful())
                        Log.d("SbsFCM", "Token: " + task.getResult());
                });
    }

    private void handleIncomingAlert(Intent intent) {
        if (intent == null) return;
        String recordType     = intent.getStringExtra("record_type");
        String recordId       = intent.getStringExtra("record_id");
        String notificationId = intent.getStringExtra("notification_id");
        String uid            = FirebaseAuth.getInstance().getUid();
        if (!TextUtils.isEmpty(notificationId) && uid != null)
            repository.markNotificationRead(uid, notificationId);
        if (!TextUtils.isEmpty(recordType) && !TextUtils.isEmpty(recordId)) {
            Intent detail = new Intent(this, RecordDetailActivity.class);
            detail.putExtra("record_type", recordType);
            detail.putExtra("record_id",   recordId);
            startActivity(detail);
            intent.removeExtra("record_type");
            intent.removeExtra("record_id");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingAlert(intent);
    }

    private void showThemePicker() {
        String[] choices = {
                getString(R.string.theme_light),
                getString(R.string.theme_dark),
                getString(R.string.theme_system)
        };
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.appearance_selector)
                .setItems(choices, (dialog, which) -> {
                    if      (which == 0) appSettingsManager.setThemeMode(AppSettingsManager.THEME_LIGHT);
                    else if (which == 1) appSettingsManager.setThemeMode(AppSettingsManager.THEME_DARK);
                    else                 appSettingsManager.setThemeMode(AppSettingsManager.THEME_SYSTEM);
                    appSettingsManager.applyTheme();
                })
                .show();
    }

    private int resolveAttrColor(int attrRes) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attrRes, tv, true);
        return tv.data;
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void openMenu() { binding.drawerLayout.openDrawer(GravityCompat.START); }
    private void hideMenu() { binding.drawerLayout.closeDrawer(GravityCompat.START); }

    // ══════════════════════════════════════════════════════════════════════════
    //  MapView lifecycle forwarding (REQUIRED by MapLibre)
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void onStart()   { super.onStart();   binding.mapView.onStart();   }

    @Override
    public void onResume() {
        super.onResume();
        binding.mapView.onResume();
        refreshAccountSwitcherUI();
        updateMapStyleLabel();
    }

    @Override
    public void onPause() {
        saveDashboardState();
        super.onPause();
        binding.mapView.onPause();
    }

    @Override
    protected void onStop()    { super.onStop();    binding.mapView.onStop();    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        binding.mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() { super.onLowMemory(); binding.mapView.onLowMemory(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        savedSightingMarkers.clear();
        binding.mapView.onDestroy();
    }
}
