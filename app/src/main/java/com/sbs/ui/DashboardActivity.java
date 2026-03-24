package com.sbs.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.sbs.R;
import com.sbs.SessionManager;
import com.sbs.data.AppSettingsManager;
import com.sbs.data.SightingRecord;
import com.sbs.data.SightingStore;
import com.sbs.data.SightingSyncManager;
import com.sbs.databinding.ActivityDashboardBinding;
import com.sbs.notifications.FcmTokenManager;
import com.sbs.utils.NetworkStatusMonitor;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsDisplay;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;

public class DashboardActivity extends BaseActivity {
    private ActivityDashboardBinding binding;
    private MyLocationNewOverlay locationOverlay;
    private boolean restoredMapState = false;
    private Marker pendingSightingMarker;
    private boolean lastNetworkOnline = false;
    private final List<Marker> savedSightingMarkers = new ArrayList<>();

    private static final String PREFS_DASHBOARD_STATE = "sbs_dashboard_state";
    private static final String KEY_MAP_LAT = "map_lat";
    private static final String KEY_MAP_LNG = "map_lng";
    private static final String KEY_MAP_ZOOM = "map_zoom";

    private AppSettingsManager appSettingsManager;
    private NetworkStatusMonitor networkStatusMonitor;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean anyGranted = false;
                for (Boolean granted : result.values()) {
                    if (Boolean.TRUE.equals(granted)) {
                        anyGranted = true;
                        break;
                    }
                }

                if (anyGranted) {
                    enableMyLocation();
                } else {
                    Toast.makeText(this, "Location permission is required for map features", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    Toast.makeText(this, "Notifications are disabled. You won't receive alerts.", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> sightingEditorLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (pendingSightingMarker != null) {
                    binding.mapView.getOverlays().remove(pendingSightingMarker);
                    pendingSightingMarker = null;
                    binding.mapView.invalidate();
                }

                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String localId = result.getData().getStringExtra(
                            SightingEditorActivity.EXTRA_SIGHTING_ID
                    );
                    if (localId != null) {
                        SightingRecord record = SightingStore.getById(this, localId);
                        if (record != null) {
                            addSightingMarker(record);
                        }
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Initialize osmdroid configuration before inflating layout
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        appSettingsManager = new AppSettingsManager(this);
        appSettingsManager.applyTheme();
        networkStatusMonitor = new NetworkStatusMonitor(this);

        super.onCreate(savedInstanceState);

        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FcmTokenManager.syncCurrentToken(this);
        bindCurrentUserFooter();

        // Capture original padding for sidePanel to preserve layout design
        int originalLeft = binding.sidePanel.getPaddingLeft();
        int originalTop = binding.sidePanel.getPaddingTop();
        int originalRight = binding.sidePanel.getPaddingRight();
        int originalBottom = binding.sidePanel.getPaddingBottom();

        // Handle Status Bar / Navigation Bar insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            
            // Apply top padding to the main content's toolbar or container
            binding.mainContent.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            
            // Apply padding to the side panel while preserving original design padding
            binding.sidePanel.setPadding(
                    originalLeft,
                    originalTop + systemBars.top,
                    originalRight,
                    originalBottom + systemBars.bottom
            );
            return insets;
        });

        requestNotificationPermissionIfNeeded();
        logFcmToken();
        handleIncomingAlert(getIntent());

        setupMap();
        setupMyLocationOverlay();
        setupMapInteractions();
        renderStoredSightings();

        if (appSettingsManager.isShowSampleMarkersEnabled()) {
            loadSampleSightings();
        }

        restoreDashboardState();

        if (!restoredMapState && appSettingsManager.isAutoCenterMapEnabled()) {
            centerMapOnUser();
        }

        setupClickListeners();

        // Handle back press to close drawer if open
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

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void logFcmToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w("SbsFCM", "Fetching FCM registration token failed", task.getException());
                        return;
                    }

                    String token = task.getResult();
                    Log.d("SbsFCM", "Current token: " + token);
                });
    }

    private void handleIncomingAlert(Intent intent) {
        if (intent == null) return;

        String alertType = intent.getStringExtra("alert_type");
        if (alertType == null) return;

        switch (alertType) {
            case "sighting":
            case "new_sighting":
                Toast.makeText(this, "New sighting alert received", Toast.LENGTH_SHORT).show();
                break;

            case "classification_ready":
                Toast.makeText(this, "Image classification result is ready", Toast.LENGTH_SHORT).show();
                break;

            case "sync_failed":
                Toast.makeText(this, "Sync failed. Check sync status.", Toast.LENGTH_SHORT).show();
                break;

            case "sync_success":
                Toast.makeText(this, "Sync completed successfully", Toast.LENGTH_SHORT).show();
                break;

            case "health_observation":
                Toast.makeText(this, "Health observation reminder", Toast.LENGTH_SHORT).show();
                break;

            case "patrol_reminder":
                Toast.makeText(this, "Patrol reminder received", Toast.LENGTH_SHORT).show();
                break;

            default:
                Toast.makeText(this, "Notification opened", Toast.LENGTH_SHORT).show();
                break;
        }

        intent.removeExtra("alert_type");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingAlert(intent);
    }

    private void setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK);
        binding.mapView.setMultiTouchControls(true);
        binding.mapView.getZoomController().setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.ALWAYS);
        binding.mapView.getZoomController().getDisplay().setPositions(false, CustomZoomButtonsDisplay.HorizontalPosition.RIGHT, CustomZoomButtonsDisplay.VerticalPosition.CENTER);

        IMapController mapController = binding.mapView.getController();
        
        // Bwindi Impenetrable region as a sample meaningful default center
        GeoPoint defaultCenter = new GeoPoint(-1.0522, 29.6201);
        mapController.setZoom(14.5);
        mapController.setCenter(defaultCenter);
    }

    private void setupMyLocationOverlay() {
        if (hasLocationPermissions()) {
            enableMyLocation();
            if (locationOverlay != null && !restoredMapState) {
                locationOverlay.runOnFirstFix(() -> runOnUiThread(() -> {
                    GeoPoint myLocation = locationOverlay.getMyLocation();
                    if (myLocation != null) {
                        binding.mapView.getController().animateTo(myLocation);
                    }
                }));
            }
        } else {
            requestLocationPermissions();
        }
    }

    private void setupMapInteractions() {
        MapEventsReceiver receiver = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                return false;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                dropPendingSightingMarker(p);
                return true;
            }
        };

        binding.mapView.getOverlays().add(new MapEventsOverlay(receiver));
    }

    private void loadSampleSightings() {
        dropSightingMarker(
                new GeoPoint(-1.0550, 29.6235),
                "Silverback Group A",
                "Observed near patrol route",
                false
        );

        dropSightingMarker(
                new GeoPoint(-1.0493, 29.6178),
                "Nest Site",
                "Recent gorilla activity recorded",
                false
        );

        dropSightingMarker(
                new GeoPoint(-1.0608, 29.6281),
                "Health Check Point",
                "Observation follow-up required",
                false
        );
    }

    private void dropSightingMarker(GeoPoint point, String title, String description, boolean addToSavedList) {
        Marker marker = new Marker(binding.mapView);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle(title);
        marker.setSubDescription(
                description
                        + "\nLat: " + point.getLatitude()
                        + "\nLng: " + point.getLongitude()
        );

        marker.setOnMarkerClickListener((m, mapView) -> {
            m.showInfoWindow();
            return true;
        });

        if (addToSavedList) {
            savedSightingMarkers.add(marker);
        }
        binding.mapView.getOverlays().add(marker);

        binding.mapView.invalidate();
    }

    private void dropPendingSightingMarker(GeoPoint point) {
        if (pendingSightingMarker != null) {
            binding.mapView.getOverlays().remove(pendingSightingMarker);
        }

        Marker marker = new Marker(binding.mapView);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setIcon(ContextCompat.getDrawable(this, android.R.drawable.ic_input_add));
        marker.setTitle("New sighting");
        marker.setOnMarkerClickListener((m, mapView) -> {
            openSightingEditor(point);
            return true;
        });

        pendingSightingMarker = marker;
        binding.mapView.getOverlays().add(marker);
        binding.mapView.invalidate();
        Toast.makeText(this, "Tap the pin to add sighting details", Toast.LENGTH_SHORT).show();
    }

    private void openSightingEditor(GeoPoint point) {
        Intent intent = new Intent(this, SightingEditorActivity.class);
        intent.putExtra(SightingEditorActivity.EXTRA_LAT, point.getLatitude());
        intent.putExtra(SightingEditorActivity.EXTRA_LNG, point.getLongitude());
        sightingEditorLauncher.launch(intent);
    }

    private void renderStoredSightings() {
        for (Marker marker : savedSightingMarkers) {
            binding.mapView.getOverlays().remove(marker);
        }
        savedSightingMarkers.clear();

        for (SightingRecord record : SightingStore.getAll(this)) {
            addSightingMarker(record);
        }
        binding.mapView.invalidate();
    }

    private void addSightingMarker(SightingRecord record) {
        Marker marker = new Marker(binding.mapView);
        marker.setPosition(new GeoPoint(record.lat, record.lng));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle(record.title);
        marker.setSubDescription(buildSightingDescription(record));
        marker.setIcon(resolveSightingIcon(record));
        marker.setOnMarkerClickListener((m, mapView) -> {
            m.showInfoWindow();
            return true;
        });

        savedSightingMarkers.add(marker);
        binding.mapView.getOverlays().add(marker);
    }

    private String buildSightingDescription(SightingRecord record) {
        String statusLabel = getString(R.string.pending_sync);
        if (SightingStore.STATUS_SYNCED.equals(record.syncStatus)) {
            statusLabel = getString(R.string.synced);
        } else if (SightingStore.STATUS_FAILED.equals(record.syncStatus)) {
            statusLabel = getString(R.string.sync_failed);
        }
        return (record.notes == null ? "" : record.notes + "\n")
                + "Lat: " + record.lat
                + "\nLng: " + record.lng
                + "\nStatus: " + statusLabel;
    }

    private android.graphics.drawable.Drawable resolveSightingIcon(SightingRecord record) {
        if (SightingStore.STATUS_SYNCED.equals(record.syncStatus)) {
            return ContextCompat.getDrawable(this, R.drawable.ic_marker_user);
        }
        if (SightingStore.STATUS_FAILED.equals(record.syncStatus)) {
            return ContextCompat.getDrawable(this, android.R.drawable.ic_dialog_alert);
        }
        return ContextCompat.getDrawable(this, android.R.drawable.ic_popup_sync);
    }

    private void saveDashboardState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_DASHBOARD_STATE, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        GeoPoint center = (GeoPoint) binding.mapView.getMapCenter();
        if (center != null) {
            editor.putString(KEY_MAP_LAT, String.valueOf(center.getLatitude()));
            editor.putString(KEY_MAP_LNG, String.valueOf(center.getLongitude()));
        }

        editor.putString(KEY_MAP_ZOOM, String.valueOf(binding.mapView.getZoomLevelDouble()));

        editor.apply();
    }

    private void restoreDashboardState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_DASHBOARD_STATE, MODE_PRIVATE);

        String latString = prefs.getString(KEY_MAP_LAT, null);
        String lngString = prefs.getString(KEY_MAP_LNG, null);
        String zoomString = prefs.getString(KEY_MAP_ZOOM, null);

        if (latString != null && lngString != null) {
            try {
                double lat = Double.parseDouble(latString);
                double lng = Double.parseDouble(lngString);
                binding.mapView.getController().setCenter(new GeoPoint(lat, lng));
                restoredMapState = true;
            } catch (NumberFormatException e) {
                Log.e("DashboardState", "Invalid saved map center", e);
            }
        }

        if (zoomString != null) {
            try {
                double zoom = Double.parseDouble(zoomString);
                binding.mapView.getController().setZoom(zoom);
            } catch (NumberFormatException e) {
                Log.e("DashboardState", "Invalid saved zoom", e);
            }
        }

    }

    private void setupClickListeners() {
        binding.btnMenuToggle.setOnClickListener(v -> openMenu());
        binding.btnMenuClose.setOnClickListener(v -> hideMenu());

        binding.menuHome.setOnClickListener(v -> hideMenu());
        binding.menuMap.setOnClickListener(v -> {
            hideMenu();
            centerMapOnUser();
        });

        binding.btnMyLocation.setOnClickListener(v -> centerMapOnUser());

        binding.menuNewSighting.setOnClickListener(v -> {
            hideMenu();
            startActivity(new Intent(DashboardActivity.this, SightingsActivity.class));
        });

        binding.menuHealthObservation.setOnClickListener(v -> {
            hideMenu();
            startActivity(new Intent(DashboardActivity.this, HealthObservationActivity.class));
        });

        binding.menuSyncStatus.setOnClickListener(v -> {
            hideMenu();
            startActivity(new Intent(DashboardActivity.this, SyncStatusActivity.class));
        });

        binding.menuDeviceInfo.setOnClickListener(v -> {
            hideMenu();
            startActivity(new Intent(DashboardActivity.this, DeviceInfoActivity.class));
        });

        binding.menuSettings.setOnClickListener(v -> {
            hideMenu();
            startActivity(new Intent(DashboardActivity.this, SettingsActivity.class));
        });

        binding.tvLogout.setOnClickListener(v -> {
            SessionManager sessionManager = new SessionManager(this);
            sessionManager.logout();
            FirebaseAuth.getInstance().signOut();

            Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        binding.btnIdentifyImage.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, ImageClassifierActivity.class)));
    }

    private void bindCurrentUserFooter() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String displayName = null;
        String email = null;
        Uri photoUrl = null;

        if (user != null) {
            displayName = user.getDisplayName();
            email = user.getEmail();
            photoUrl = user.getPhotoUrl();
        }

        String resolvedName = resolveDisplayName(displayName, email);
        binding.tvUserName.setText(resolvedName);

        if (photoUrl != null) {
            Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.bg_dashboard_avatar)
                    .error(R.drawable.bg_dashboard_avatar)
                    .into(binding.ivUserAvatar);
        } else {
            binding.ivUserAvatar.setImageResource(R.drawable.bg_dashboard_avatar);
        }

        if (user != null && TextUtils.isEmpty(displayName)) {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        String fullName = snapshot.getString("fullName");
                        if (!TextUtils.isEmpty(fullName)) {
                            binding.tvUserName.setText(fullName);
                        }
                    })
                    .addOnFailureListener(e ->
                            Log.w("DashboardUser", "Failed to load user profile", e));
        }
    }

    private String resolveDisplayName(String displayName, String email) {
        if (!TextUtils.isEmpty(displayName)) {
            return displayName;
        }

        if (!TextUtils.isEmpty(email)) {
            return formatNameFromEmail(email);
        }

        return "User";
    }

    private String formatNameFromEmail(String email) {
        String prefix = email;
        int atIndex = email.indexOf('@');
        if (atIndex > 0) {
            prefix = email.substring(0, atIndex);
        }

        String[] parts = prefix.replace('.', ' ')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .split("\\s+");

        StringBuilder formatted = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (formatted.length() > 0) {
                formatted.append(' ');
            }
            formatted.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                formatted.append(part.substring(1));
            }
        }

        return formatted.length() > 0 ? formatted.toString() : "User";
    }

    private void centerMapOnUser() {
        if (!hasLocationPermissions()) {
            requestLocationPermissions();
            return;
        }

        if (locationOverlay == null) {
            Toast.makeText(this, "Location overlay not ready yet", Toast.LENGTH_SHORT).show();
            enableMyLocation();
            return;
        }

        GeoPoint myLocation = locationOverlay.getMyLocation();
        if (myLocation != null) {
            binding.mapView.getController().setZoom(18.0);
            binding.mapView.getController().animateTo(myLocation);
        } else {
            Toast.makeText(this, "Current location not available yet", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasLocationPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissions() {
        locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private void enableMyLocation() {
        if (locationOverlay == null) {
            locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), binding.mapView);
            locationOverlay.enableMyLocation();
            binding.mapView.getOverlays().add(locationOverlay);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.mapView.onResume();
        if (locationOverlay != null) {
            locationOverlay.enableMyLocation();
        }
        if (SightingSyncManager.isOnline(this)) {
            SightingSyncManager.syncPendingSightings(this);
        }
        renderStoredSightings();
    }

    @Override
    public void onPause() {
        saveDashboardState();
        super.onPause();
        binding.mapView.onPause();
        if (locationOverlay != null) {
            locationOverlay.disableMyLocation();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (networkStatusMonitor != null) {
            networkStatusMonitor.start((label, backgroundRes) -> runOnUiThread(() -> {
                binding.tvNetworkStatus.setText(label);
                binding.tvNetworkStatus.setBackgroundResource(backgroundRes);
                boolean onlineNow = "Online".equals(label);
                if (onlineNow && !lastNetworkOnline) {
                    SightingSyncManager.syncPendingSightings(this);
                }
                lastNetworkOnline = onlineNow;
            }));
        }
    }

    @Override
    protected void onStop() {
        if (networkStatusMonitor != null) {
            networkStatusMonitor.stop();
        }
        super.onStop();
    }

    private void showMenuToast(String message) {
        hideMenu();
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void openMenu() {
        binding.drawerLayout.openDrawer(GravityCompat.START);
    }

    private void hideMenu() {
        binding.drawerLayout.closeDrawer(GravityCompat.START);
    }
}
