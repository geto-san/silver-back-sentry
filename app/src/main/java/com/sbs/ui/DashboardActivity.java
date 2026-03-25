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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.PopupWindow;
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
    private boolean lastNetworkOnline = false;
    private final List<Marker> savedSightingMarkers = new ArrayList<>();

    private static final String PREFS_DASHBOARD_STATE = "sbs_dashboard_state";
    private static final String KEY_MAP_LAT = "map_lat";
    private static final String KEY_MAP_LNG = "map_lng";
    private static final String KEY_MAP_ZOOM = "map_zoom";

    private AppSettingsManager appSettingsManager;

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

    private final ActivityResultLauncher<Intent> recordEditorLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    renderStoredSightings();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        appSettingsManager = new AppSettingsManager(this);
        appSettingsManager.applyTheme();

        super.onCreate(savedInstanceState);

        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FcmTokenManager.syncCurrentToken(this);
        bindCurrentUserFooter();

        int originalLeft = binding.sidePanel.getPaddingLeft();
        int originalTop = binding.sidePanel.getPaddingTop();
        int originalRight = binding.sidePanel.getPaddingRight();
        int originalBottom = binding.sidePanel.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.mainContent.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
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
        Toast.makeText(this, "Notification: " + alertType, Toast.LENGTH_SHORT).show();
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
        GeoPoint defaultCenter = new GeoPoint(-1.0522, 29.6201);
        mapController.setZoom(14.5);
        mapController.setCenter(defaultCenter);
    }

    private void setupMyLocationOverlay() {
        if (hasLocationPermissions()) {
            enableMyLocation();
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
                showRecordPopup(p, binding.mapView);
                return true;
            }
        };
        binding.mapView.getOverlays().add(new MapEventsOverlay(receiver));
    }

    private void showRecordPopup(GeoPoint point, View anchor) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_record_options, null);
        PopupWindow popupWindow = new PopupWindow(popupView, 
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true);

        popupView.findViewById(R.id.btnRecordSighting).setOnClickListener(v -> {
            popupWindow.dismiss();
            openSightingEditor(point);
        });
        popupView.findViewById(R.id.btnRecordHealth).setOnClickListener(v -> {
            popupWindow.dismiss();
            openHealthEditor(point);
        });
        popupView.findViewById(R.id.btnRecordPatrol).setOnClickListener(v -> {
            popupWindow.dismiss();
            openPatrolEditor(point);
        });

        popupWindow.showAtLocation(anchor, android.view.Gravity.CENTER, 0, 0);
    }

    private void openSightingEditor(GeoPoint point) {
        Intent intent = new Intent(this, SightingEditorActivity.class);
        intent.putExtra("lat", point.getLatitude());
        intent.putExtra("lng", point.getLongitude());
        recordEditorLauncher.launch(intent);
    }

    private void openHealthEditor(GeoPoint point) {
        // To be implemented
        Toast.makeText(this, "Health Observation Editor", Toast.LENGTH_SHORT).show();
    }

    private void openPatrolEditor(GeoPoint point) {
        // To be implemented
        Toast.makeText(this, "Patrol Log Editor", Toast.LENGTH_SHORT).show();
    }

    private void loadSampleSightings() {
        dropSightingMarker(new GeoPoint(-1.0550, 29.6235), "Silverback Group A", "Observed near patrol route", false);
    }

    private void dropSightingMarker(GeoPoint point, String title, String description, boolean addToSavedList) {
        Marker marker = new Marker(binding.mapView);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle(title);
        marker.setSubDescription(description);
        if (addToSavedList) savedSightingMarkers.add(marker);
        binding.mapView.getOverlays().add(marker);
        binding.mapView.invalidate();
    }

    private void renderStoredSightings() {
        for (Marker marker : savedSightingMarkers) binding.mapView.getOverlays().remove(marker);
        savedSightingMarkers.clear();
        for (SightingRecord record : SightingStore.getAll(this)) addSightingMarker(record);
        binding.mapView.invalidate();
    }

    private void addSightingMarker(SightingRecord record) {
        Marker marker = new Marker(binding.mapView);
        marker.setPosition(new GeoPoint(record.lat, record.lng));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle(record.title);
        marker.setSubDescription(record.notes);
        savedSightingMarkers.add(marker);
        binding.mapView.getOverlays().add(marker);
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
                binding.mapView.getController().setCenter(new GeoPoint(Double.parseDouble(latString), Double.parseDouble(lngString)));
                restoredMapState = true;
            } catch (Exception ignored) {}
        }
        if (zoomString != null) {
            try {
                binding.mapView.getController().setZoom(Double.parseDouble(zoomString));
            } catch (Exception ignored) {}
        }
    }

    private void setupClickListeners() {
        binding.btnMenuToggle.setOnClickListener(v -> openMenu());
        binding.btnMenuClose.setOnClickListener(v -> hideMenu());
        binding.btnMyLocation.setOnClickListener(v -> {
            if (hasLocationPermissions() && locationOverlay != null) {
                GeoPoint myLoc = locationOverlay.getMyLocation();
                if (myLoc != null) showRecordPopup(myLoc, binding.mapView);
                else centerMapOnUser();
            } else {
                requestLocationPermissions();
            }
        });
        binding.menuNewSighting.setOnClickListener(v -> {
            hideMenu();
            startActivity(new Intent(this, SightingsActivity.class));
        });
        binding.menuPatrolLogs.setOnClickListener(v -> {
            hideMenu();
            startActivity(new Intent(this, PatrolLogsActivity.class));
        });
        binding.menuDeviceInfo.setOnClickListener(v -> {
            hideMenu();
            startActivity(new Intent(this, DeviceInfoActivity.class));
        });
        binding.menuSettings.setOnClickListener(v -> {
            hideMenu();
            startActivity(new Intent(this, SettingsActivity.class));
        });
        binding.tvLogout.setOnClickListener(v -> {
            new SessionManager(this).logout();
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
        binding.btnIdentifyImage.setOnClickListener(v -> startActivity(new Intent(this, ImageClassifierActivity.class)));
    }

    private void bindCurrentUserFooter() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            binding.tvUserName.setText(user.getDisplayName() != null ? user.getDisplayName() : user.getEmail());
            if (user.getPhotoUrl() != null) {
                Glide.with(this).load(user.getPhotoUrl()).placeholder(R.drawable.bg_dashboard_avatar).into(binding.ivUserAvatar);
            }
        }
    }

    private void centerMapOnUser() {
        if (!hasLocationPermissions()) {
            requestLocationPermissions();
            return;
        }
        if (locationOverlay == null) {
            enableMyLocation();
            return;
        }
        GeoPoint myLocation = locationOverlay.getMyLocation();
        if (myLocation != null) {
            binding.mapView.getController().setZoom(18.0);
            binding.mapView.getController().animateTo(myLocation);
        }
    }

    private boolean hasLocationPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissions() {
        locationPermissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
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
        if (locationOverlay != null) locationOverlay.enableMyLocation();
        renderStoredSightings();
    }

    @Override
    public void onPause() {
        saveDashboardState();
        super.onPause();
        binding.mapView.onPause();
        if (locationOverlay != null) locationOverlay.disableMyLocation();
    }

    @Override
    public void onStatusChanged(boolean isOnline, boolean isAirplaneModeOn) {
        super.onStatusChanged(isOnline, isAirplaneModeOn);
        if (isOnline && !lastNetworkOnline) {
            SightingSyncManager.syncAllPending(this);
        }
        lastNetworkOnline = isOnline;
    }

    private void openMenu() { binding.drawerLayout.openDrawer(GravityCompat.START); }
    private void hideMenu() { binding.drawerLayout.closeDrawer(GravityCompat.START); }
}
