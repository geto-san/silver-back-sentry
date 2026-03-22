package com.sbs.ui;

// ─────────────────────────────────────────────────────────────────────────────
// ImageClassifierActivity.java
//
// PURPOSE:
//   Lets the user pick an image from their gallery (or capture one with the
//   camera) and runs Google ML Kit's ON-DEVICE image labeling model to identify
//   what is in the image.  No internet connection is required — the model
//   (.tflite file) is bundled inside the APK via the ML Kit dependency.
//
// FLOW (numbered in comments throughout the file):
//   1. Activity starts → UI is inflated, click listeners attached
//   2. User taps "Choose Image" → system gallery picker launches
//   3. User selects an image  → onActivityResult delivers the URI
//   4. URI is decoded into an InputImage (ML Kit wrapper)
//   5. ImageLabeler runs on the InputImage (on a background thread internally)
//   6. Results arrive in an OnSuccessListener as a List<ImageLabel>
//   7. Labels are sorted by confidence and displayed in the RecyclerView / TextView
//   8. User taps "Capture with Camera" → camera intent launches (steps 3-7 repeat)
//
// DEPENDENCIES to add in build.gradle (:app) before this compiles:
//   implementation 'com.google.mlkit:image-labeling:17.0.9'
//   // For the camera intent helper (already in most projects):
//   implementation 'androidx.activity:activity:1.9.0'
//
// MANIFEST additions required:
//   <!-- Camera permission -->
//   <uses-permission android:name="android.permission.CAMERA" />
//   <!-- FileProvider for camera capture URI on Android 7+ -->
//   <provider
//       android:name="androidx.core.content.FileProvider"
//       android:authorities="${applicationId}.fileprovider"
//       android:exported="false"
//       android:grantUriPermissions="true">
//       <meta-data
//           android:name="android.support.FILE_PROVIDER_PATHS"
//           android:resource="@xml/file_paths" />
//   </provider>
//   <!-- Add the activity itself -->
//   <activity android:name=".ui.ImageClassifierActivity" />
//
// res/xml/file_paths.xml  (create this file):
//   <?xml version="1.0" encoding="utf-8"?>
//   <paths>
//       <external-files-path name="camera_images" path="Pictures/" />
//   </paths>
// ─────────────────────────────────────────────────────────────────────────────

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.sbs.databinding.ActivityImageClassifierBinding;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ImageClassifierActivity extends AppCompatActivity {

    // ── 1. Constants ──────────────────────────────────────────────────────────

    /**
     * Minimum confidence score (0.0 – 1.0) a label must reach before we show
     * it to the user.  0.65 = 65 % confident.  Lower this to see more (noisier)
     * results; raise it for stricter filtering.
     */
    private static final float CONFIDENCE_THRESHOLD = 0.65f;

    /**
     * Maximum number of labels we display.  ML Kit can return up to ~20; we
     * cap at 8 so the list stays readable.
     */
    private static final int MAX_LABELS_TO_SHOW = 8;

    /** Request code used when we ask for CAMERA permission at runtime. */
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 101;

    // ── 2. Member Variables ───────────────────────────────────────────────────

    /** View-binding instance — replaces findViewById() throughout the class. */
    private ActivityImageClassifierBinding binding;

    /**
     * The ML Kit labeler.  Created once in onCreate() and reused for every
     * image — creating it is cheap, but reuse avoids any repeated setup cost.
     *
     * ImageLabelerOptions.DEFAULT_OPTIONS uses the bundled MobileNet model
     * with a built-in 0.5 confidence threshold; we override it with our own.
     */
    private ImageLabeler imageLabeler;

    /**
     * URI of the photo file the camera will write to.
     * Stored as a field so onActivityResult() can access it after the
     * camera Activity finishes.
     */
    private Uri cameraImageUri;

    // ── 3. Activity Result Launchers (modern replacement for onActivityResult) ─

    /**
     * Launcher for the system gallery picker.
     * Registered before onCreate() completes, as required by the API.
     */
    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        // 3a. Gallery returned a result
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Uri imageUri = result.getData().getData(); // URI of chosen image
                            if (imageUri != null) {
                                loadAndClassifyFromUri(imageUri); // → step 4
                            }
                        }
                    });

    /**
     * Launcher for the built-in Camera app.
     * The camera writes the full-res photo to cameraImageUri (set in step 8).
     */
    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        // 3b. Camera returned a result
                        if (result.getResultCode() == RESULT_OK && cameraImageUri != null) {
                            loadAndClassifyFromUri(cameraImageUri); // → step 4
                        }
                    });

    // ── 4. onCreate ───────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inflate layout via view-binding
        binding = ActivityImageClassifierBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // ── 4a. Initialise the ML Kit ImageLabeler ────────────────────────────
        //
        // ImageLabelerOptions lets us tweak two things:
        //   • setConfidenceThreshold() – ML Kit itself filters out low-confidence
        //     labels before passing results to our listener.
        //   • We also cap the list length ourselves in displayResults().
        //
        ImageLabelerOptions options = new ImageLabelerOptions.Builder()
                .setConfidenceThreshold(CONFIDENCE_THRESHOLD)
                .build();

        // ImageLabeling.getClient() returns a singleton-style labeler bound to
        // the on-device MobileNet V2 model bundled with ML Kit.
        imageLabeler = ImageLabeling.getClient(options);

        // ── 4b. Wire up UI buttons ────────────────────────────────────────────

        // "Choose from Gallery" button → open the system image picker
        binding.btnChooseImage.setOnClickListener(v -> openGallery()); // → step 5a

        // "Take Photo" button → request camera permission, then open camera
        binding.btnCaptureImage.setOnClickListener(v -> requestCameraAndCapture()); // → step 8

        // Back arrow / close button → return to previous screen
        binding.btnBack.setOnClickListener(v -> finish());
    }

    // ── 5. Gallery Flow ───────────────────────────────────────────────────────

    /**
     * Step 5a – Builds an ACTION_GET_CONTENT Intent that filters to image/*
     * and hands it to galleryLauncher.  The system shows the user their gallery
     * (or a picker sheet if multiple apps can handle the intent).
     */
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");                  // Only show image files
        intent.addCategory(Intent.CATEGORY_OPENABLE); // Must be openable via InputStream
        galleryLauncher.launch(Intent.createChooser(intent, "Select Image"));
    }

    // ── 6. Shared: Load image from URI and run classification ─────────────────

    /**
     * Step 6 – Given any content:// or file:// URI, this method:
     *   a) Decodes it to a Bitmap and shows it in the ImageView
     *   b) Wraps it in an ML Kit InputImage
     *   c) Passes it to runClassification()
     *
     * @param uri  The URI of the image to classify (from gallery or camera).
     */
    private void loadAndClassifyFromUri(@NonNull Uri uri) {

        // 6a. Show loading state so the user knows something is happening
        showLoadingState(true);
        binding.tvResults.setText(""); // Clear any previous results

        try {
            // 6b. Open an InputStream for the URI and decode it to a Bitmap.
            //     We down-sample large images to avoid OOM errors on low-end devices.
            InputStream inputStream = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

            if (bitmap == null) {
                showError("Could not decode the selected image.");
                showLoadingState(false);
                return;
            }

            // 6c. Display the image in the preview ImageView
            binding.ivPreview.setImageBitmap(bitmap);
            binding.ivPreview.setVisibility(View.VISIBLE);

            // 6d. Wrap the Bitmap in an InputImage — ML Kit's universal image
            //     container.  fromBitmap() is the simplest path; alternatives
            //     include fromMediaImage() (camera2 API) and fromFilePath().
            InputImage inputImage = InputImage.fromBitmap(bitmap, 0 /* rotation degrees */);

            // 6e. Hand off to the classification step
            runClassification(inputImage); // → step 7

        } catch (IOException e) {
            showError("Failed to open image: " + e.getMessage());
            showLoadingState(false);
        }
    }

    // ── 7. ML Kit Classification ──────────────────────────────────────────────

    /**
     * Step 7 – Submits the InputImage to the on-device ImageLabeler.
     *
     * ML Kit runs inference on a background thread automatically.  The
     * OnSuccessListener / OnFailureListener callbacks are delivered on the
     * MAIN thread, so it is safe to update UI directly inside them.
     *
     * @param image  ML Kit InputImage ready for inference.
     */
    private void runClassification(@NonNull InputImage image) {

        imageLabeler.process(image)
                // ── 7a. SUCCESS ──────────────────────────────────────────────
                .addOnSuccessListener(labels -> {
                    // `labels` is a List<ImageLabel>, already filtered to
                    // >= CONFIDENCE_THRESHOLD and sorted by confidence DESC.
                    showLoadingState(false);

                    if (labels.isEmpty()) {
                        // Model ran fine but found nothing above the threshold
                        binding.tvResults.setText(
                                "No recognisable objects found.\n" +
                                        "Try a clearer photo or lower the confidence threshold.");
                    } else {
                        displayResults(labels); // → step 7b
                    }
                })
                // ── 7b. FAILURE ──────────────────────────────────────────────
                .addOnFailureListener(e -> {
                    showLoadingState(false);
                    showError("Classification failed: " + e.getMessage());
                });
    }

    /**
     * Step 7b – Formats the list of ImageLabel objects into a human-readable
     * string and writes it to the results TextView.
     *
     * Each ImageLabel exposes:
     *   • getText()       – a human-readable English description (e.g. "Dog")
     *   • getConfidence() – float 0.0–1.0 (e.g. 0.97 = 97 % confident)
     *   • getIndex()      – numeric ID in the model's label map (rarely needed)
     *
     * @param labels  Non-empty list of labels sorted by confidence (highest first).
     */
    private void displayResults(@NonNull List<ImageLabel> labels) {

        StringBuilder sb = new StringBuilder();
        sb.append("🔍 Identified Objects:\n\n");

        // Iterate up to MAX_LABELS_TO_SHOW items
        int count = Math.min(labels.size(), MAX_LABELS_TO_SHOW);

        for (int i = 0; i < count; i++) {

            ImageLabel label = labels.get(i);

            // Convert confidence float (0.0–1.0) to a readable percentage string
            int confidencePct = Math.round(label.getConfidence() * 100);

            // Build a visual confidence bar (e.g. "████████░░" for 80 %)
            String bar = buildConfidenceBar(label.getConfidence());

            // ── Numbered label line ─────────────────────────────────────────
            // Format: "1. Dog  ████████░░  97%"
            sb.append(i + 1)                        // Label number (1-based)
                    .append(". ")
                    .append(label.getText())        // e.g. "Dog"
                    .append("\n   ")
                    .append(bar)                    // visual bar
                    .append("  ")
                    .append(confidencePct)
                    .append("%\n\n");
        }

        // If ML Kit returned more labels than we display, mention it
        if (labels.size() > MAX_LABELS_TO_SHOW) {
            sb.append("… and ")
                    .append(labels.size() - MAX_LABELS_TO_SHOW)
                    .append(" more below threshold.");
        }

        binding.tvResults.setText(sb.toString());
    }

    /**
     * Builds a 10-character Unicode block bar to visualise a confidence score.
     *
     * Example: confidence = 0.75  →  "███████░░░"
     *
     * @param confidence  Value between 0.0 and 1.0.
     * @return            10-char string of filled/empty block characters.
     */
    private String buildConfidenceBar(float confidence) {
        int filled = Math.round(confidence * 10); // how many filled blocks (0-10)
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? "█" : "░");
        }
        return bar.toString();
    }

    // ── 8. Camera Flow ────────────────────────────────────────────────────────

    /**
     * Step 8a – Check whether we hold CAMERA permission at runtime.
     *   • Android 6.0+ (API 23+) requires explicit user approval.
     *   • If already granted, launch the camera directly.
     *   • If not, show the system permission dialog.
     */
    private void requestCameraAndCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            // Permission already held → go straight to camera
            launchCamera(); // → step 8b
        } else {
            // Ask the user for permission
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CODE_CAMERA_PERMISSION);
        }
    }

    /**
     * Step 8b – Called after requestPermissions() finishes.
     * Launches the camera if the user granted permission; shows an error if not.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // User tapped "Allow" → launch camera
                launchCamera();
            } else {
                // User tapped "Deny"
                Toast.makeText(this,
                        "Camera permission is required to take a photo.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Step 8c – Creates a temporary file for the full-resolution camera photo,
     * wraps it in a FileProvider URI (required on Android 7+), and fires the
     * ACTION_IMAGE_CAPTURE Intent.
     *
     * We use FileProvider rather than a plain file:// URI because direct file
     * paths to the app's private storage are blocked by the OS since API 24.
     */
    private void launchCamera() {
        try {
            // 8c-1. Create an empty file in external storage "Pictures" folder.
            //       The timestamp in the name ensures uniqueness between shots.
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date());
            String imageFileName = "CLASSIFY_" + timeStamp + ".jpg";

            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File imageFile  = new File(storageDir, imageFileName);

            // 8c-2. Convert the file path to a content:// URI via FileProvider.
            //       The authority string must match the one declared in AndroidManifest.xml.
            cameraImageUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider", // authority
                    imageFile);

            // 8c-3. Build the camera Intent and point it at our output URI.
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);

            // 8c-4. Launch the camera; result is delivered to cameraLauncher
            cameraLauncher.launch(cameraIntent);

        } catch (Exception e) {
            showError("Could not launch camera: " + e.getMessage());
        }
    }

    // ── 9. UI Helpers ─────────────────────────────────────────────────────────

    /**
     * Step 9a – Toggles the ProgressBar and dims the action buttons while ML
     * Kit is processing an image, preventing double-taps.
     *
     * @param loading  true = show spinner & disable buttons; false = hide & re-enable.
     */
    private void showLoadingState(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnChooseImage.setEnabled(!loading);
        binding.btnCaptureImage.setEnabled(!loading);
    }

    /**
     * Step 9b – Shows an error message in the results area and logs it.
     *
     * @param message  Human-readable description of what went wrong.
     */
    private void showError(@NonNull String message) {
        binding.tvResults.setText("⚠️ " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // ── 10. Lifecycle ──────────────────────────────────────────────────────────

    /**
     * Step 10 – Release the ImageLabeler when the Activity is destroyed to free
     * native TFLite resources.  Forgetting this can cause native memory leaks.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (imageLabeler != null) {
            imageLabeler.close();
        }
    }
}
