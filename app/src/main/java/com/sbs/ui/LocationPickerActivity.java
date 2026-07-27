package com.sbs.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.sbs.R;
import com.sbs.databinding.ActivityLocationPickerBinding;

import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.location.LocationComponent;
import org.maplibre.android.location.LocationComponentActivationOptions;
import org.maplibre.android.location.modes.CameraMode;
import org.maplibre.android.location.modes.RenderMode;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;

/**
 * LocationPickerActivity
 *
 * Feature 4 — Interactive Map Module (location picker variant).
 *
 * Allows the ranger to select a precise GPS coordinate for a new sighting
 * or health observation.  The flow is:
 *
 *   1. Activity opens with a full-screen MapLibre map.
 *   2. The ranger long-presses anywhere on the map to drop a red pin.
 *   3. A card at the bottom shows the selected lat / lng.
 *   4. Tapping "Confirm Location" returns the coordinates to the caller
 *      via {@link android.app.Activity#setResult(int, Intent)}.
 *   5. "My Location" FAB centres the camera on the device's GPS fix and
 *      optionally places the pin there.
 *   6. "Clear pin" removes the dropped marker and disables the confirm button.
 *
 * Caller usage (in SightingEditorActivity):
 * <pre>
 *   locationPickerLauncher.launch(new Intent(this, LocationPickerActivity.class));
 *   // In the result callback:
 *   double lat = data.getDoubleExtra(RESULT_LAT, 0.0);
 *   double lng = data.getDoubleExtra(RESULT_LNG, 0.0);
 * </pre>
 *
 * Optional extras the caller can pass IN:
 *   EXTRA_INITIAL_LAT / EXTRA_INITIAL_LNG  — if non-zero, places an initial
 *   marker at that coordinate (useful when editing an existing record).
 *
 * Feature 5 — Lifecycle Controller:
 *   All MapView lifecycle events (onCreate, onStart, onResume, onPause,
 *   onStop, onSaveInstanceState, onLowMemory, onDestroy) are forwarded
 *   explicitly.  MapLibre requires this — skipping any of them causes
 *   rendering glitches or resource leaks.
 */
public class LocationPickerActivity extends AppCompatActivity implements OnMapReadyCallback {

    // ── Intent contract ───────────────────────────────────────────────────────

    /** Optional: initial latitude to centre and pre-pin the map on. */
    public static final String EXTRA_INITIAL_LAT = "initial_lat";
    /** Optional: initial longitude to centre and pre-pin the map on. */
    public static final String EXTRA_INITIAL_LNG = "initial_lng";

    /** Result key for the confirmed latitude. */
    public static final String RESULT_LAT = "result_lat";
    /** Result key for the confirmed longitude. */
    public static final String RESULT_LNG = "result_lng";

    // ── Map style ─────────────────────────────────────────────────────────────

    private static final String MAP_STYLE_URL = "https://demotiles.maplibre.org/style.json";

    /** Default camera position — Bwindi Impenetrable Forest, Uganda. */
    private static final double DEFAULT_LAT  = -1.0333;
    private static final double DEFAULT_LNG  = 29.6167;
    private static final double DEFAULT_ZOOM = 10.0;

    // ── State ─────────────────────────────────────────────────────────────────

    private ActivityLocationPickerBinding binding;
    private MapView    mapView;
    private MapLibreMap mapLibreMap;

    private Marker  droppedMarker;      // the single draggable pin
    private double  selectedLat = 0.0;
    private double  selectedLng = 0.0;
    private boolean pinConfirmed = false;

    // ── Permission launcher ───────────────────────────────────────────────────

    private final ActivityResultLauncher<String[]> locationPermLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    granted -> {
                        boolean any = Boolean.TRUE.equals(granted.get(android.Manifest.permission.ACCESS_FINE_LOCATION))
                                   || Boolean.TRUE.equals(granted.get(android.Manifest.permission.ACCESS_COARSE_LOCATION));
                        if (any && mapLibreMap != null) {
                            mapLibreMap.getStyle(this::activateLocationComponent);
                        }
                    }
            );

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MapLibre.getInstance(this);
        super.onCreate(savedInstanceState);

        binding = ActivityLocationPickerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mapView = binding.mapView;
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        setupToolbar();
        setupConfirmButton();
        setupClearButton();
    }

    @Override
    public void onMapReady(@NonNull MapLibreMap map) {
        this.mapLibreMap = map;

        map.setStyle(new Style.Builder().fromUri(MAP_STYLE_URL), style -> {
            // ── Centre the camera ─────────────────────────────────────────────
            double initLat = getIntent().getDoubleExtra(EXTRA_INITIAL_LAT, 0.0);
            double initLng = getIntent().getDoubleExtra(EXTRA_INITIAL_LNG, 0.0);

            if (initLat != 0.0 || initLng != 0.0) {
                // Caller provided an existing coordinate — centre and pre-pin it
                moveCameraTo(initLat, initLng, 14.0);
                placePin(new LatLng(initLat, initLng));
            } else {
                // No prior coordinate — default to the gorilla habitat
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(DEFAULT_LAT, DEFAULT_LNG), DEFAULT_ZOOM));
            }

            // ── Long-press to drop / move pin ─────────────────────────────────
            map.addOnMapLongClickListener(point -> {
                placePin(point);
                return true;   // consume the event
            });

            // ── Activate location component if permission is already granted ──
            if (hasLocationPermission()) {
                activateLocationComponent(style);
            }
        });

        // ── My-location FAB ───────────────────────────────────────────────────
        binding.fabMyLocation.setOnClickListener(v -> onMyLocationClicked());
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    // ── Confirm / clear buttons ───────────────────────────────────────────────

    private void setupConfirmButton() {
        binding.fabConfirmLocation.setOnClickListener(v -> {
            if (selectedLat == 0.0 && selectedLng == 0.0) {
                Toast.makeText(this,
                        "Long-press the map to select a location first.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Intent result = new Intent();
            result.putExtra(RESULT_LAT, selectedLat);
            result.putExtra(RESULT_LNG, selectedLng);
            setResult(RESULT_OK, result);
            finish();
        });
    }

    private void setupClearButton() {
        binding.btnClearPin.setOnClickListener(v -> clearPin());
    }

    // ── Pin management ────────────────────────────────────────────────────────

    /**
     * Places (or moves) the single dropped pin to {@code point}.
     * Updates the coordinate display card and enables the confirm button.
     */
    private void placePin(LatLng point) {
        if (mapLibreMap == null) return;

        // Remove any existing pin
        if (droppedMarker != null) {
            mapLibreMap.removeMarker(droppedMarker);
        }

        droppedMarker = mapLibreMap.addMarker(new MarkerOptions()
                .position(point)
                .title("Sighting location")
                .snippet(formatCoordinate(point.getLatitude(), point.getLongitude())));

        selectedLat = point.getLatitude();
        selectedLng = point.getLongitude();

        // Update coordinate display
        binding.tvLatitude.setText(String.format("Lat: %.6f", selectedLat));
        binding.tvLongitude.setText(String.format("Lng: %.6f", selectedLng));

        // Show the coordinates card
        binding.cardCoordinates.setVisibility(View.VISIBLE);
        binding.cardInstruction.setVisibility(View.GONE);

        // Enable the confirm FAB
        binding.fabConfirmLocation.setEnabled(true);

        // Gently animate the camera to the pin
        moveCameraTo(selectedLat, selectedLng, 15.0);
    }

    /** Removes the dropped pin and resets UI state. */
    private void clearPin() {
        if (droppedMarker != null && mapLibreMap != null) {
            mapLibreMap.removeMarker(droppedMarker);
            droppedMarker = null;
        }
        selectedLat = 0.0;
        selectedLng = 0.0;
        binding.cardCoordinates.setVisibility(View.GONE);
        binding.cardInstruction.setVisibility(View.VISIBLE);
        binding.fabConfirmLocation.setEnabled(false);
    }

    // ── My location ───────────────────────────────────────────────────────────

    private void onMyLocationClicked() {
        if (!hasLocationPermission()) {
            locationPermLauncher.launch(new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }
        if (mapLibreMap == null) return;

        LocationComponent lc = mapLibreMap.getLocationComponent();
        if (lc.isLocationComponentActivated() && lc.getLastKnownLocation() != null) {
            double myLat = lc.getLastKnownLocation().getLatitude();
            double myLng = lc.getLastKnownLocation().getLongitude();
            placePin(new LatLng(myLat, myLng));
        } else {
            Toast.makeText(this,
                    "Waiting for GPS fix — try again in a moment.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressWarnings("MissingPermission")
    private void activateLocationComponent(Style style) {
        LocationComponent locationComponent = mapLibreMap.getLocationComponent();
        locationComponent.activateLocationComponent(
                LocationComponentActivationOptions.builder(this, style).build()
        );
        locationComponent.setLocationComponentEnabled(true);
        locationComponent.setCameraMode(CameraMode.NONE);
        locationComponent.setRenderMode(RenderMode.COMPASS);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void moveCameraTo(double lat, double lng, double zoom) {
        if (mapLibreMap == null) return;
        mapLibreMap.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                        new CameraPosition.Builder()
                                .target(new LatLng(lat, lng))
                                .zoom(zoom)
                                .build()
                ),
                500
        );
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private String formatCoordinate(double lat, double lng) {
        return String.format("%.6f, %.6f", lat, lng);
    }

    // ── MapView lifecycle forwarding (REQUIRED by MapLibre) ───────────────────
    // MapLibre manages its own OpenGL surface and GPS resources through the
    // Android lifecycle.  Every one of these calls must be forwarded or the
    // map will render incorrectly or leak resources.

    @Override protected void onStart()   { super.onStart();   mapView.onStart();   }
    @Override protected void onResume()  { super.onResume();  mapView.onResume();  }
    @Override protected void onPause()   { super.onPause();   mapView.onPause();   }
    @Override protected void onStop()    { super.onStop();    mapView.onStop();    }
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDestroy(); }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}
