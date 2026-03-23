package com.sbs.ui;

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

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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

    private static final float CONFIDENCE_THRESHOLD = 0.65f;
    private static final int MAX_LABELS_TO_SHOW = 8;
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 101;

    private ActivityImageClassifierBinding binding;
    private ImageLabeler imageLabeler;
    private Uri cameraImageUri;

    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Uri imageUri = result.getData().getData();
                            if (imageUri != null) {
                                loadAndClassifyFromUri(imageUri);
                            }
                        }
                    });

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && cameraImageUri != null) {
                            loadAndClassifyFromUri(cameraImageUri);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityImageClassifierBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageLabelerOptions options = new ImageLabelerOptions.Builder()
                .setConfidenceThreshold(CONFIDENCE_THRESHOLD)
                .build();
        imageLabeler = ImageLabeling.getClient(options);

        binding.btnChooseImage.setOnClickListener(v -> openGallery());
        binding.btnCaptureImage.setOnClickListener(v -> requestCameraAndCapture());
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        galleryLauncher.launch(Intent.createChooser(intent, "Select Image"));
    }

    private void loadAndClassifyFromUri(@NonNull Uri uri) {
        showLoadingState(true);
        binding.tvResults.setText("");

        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

            if (bitmap == null) {
                showError("Could not decode the selected image.");
                showLoadingState(false);
                return;
            }

            binding.ivPreview.setImageBitmap(bitmap);
            binding.ivPreview.setVisibility(View.VISIBLE);

            InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
            runClassification(inputImage);

        } catch (IOException e) {
            showError("Failed to open image: " + e.getMessage());
            showLoadingState(false);
        }
    }

    private void runClassification(@NonNull InputImage image) {
        imageLabeler.process(image)
                .addOnSuccessListener(labels -> {
                    showLoadingState(false);
                    if (labels.isEmpty()) {
                        binding.tvResults.setText("No recognisable objects found.");
                    } else {
                        displayResults(labels);
                    }
                })
                .addOnFailureListener(e -> {
                    showLoadingState(false);
                    showError("Classification failed: " + e.getMessage());
                });
    }

    private void displayResults(@NonNull List<ImageLabel> labels) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔍 Identified Objects:\n\n");
        int count = Math.min(labels.size(), MAX_LABELS_TO_SHOW);
        for (int i = 0; i < count; i++) {
            ImageLabel label = labels.get(i);
            int confidencePct = Math.round(label.getConfidence() * 100);
            sb.append(i + 1).append(". ").append(label.getText())
                    .append(" (").append(confidencePct).append("%)\n\n");
        }
        binding.tvResults.setText(sb.toString());
    }

    private void requestCameraAndCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CODE_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                Toast.makeText(this, "Camera permission required.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void launchCamera() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String imageFileName = "CLASSIFY_" + timeStamp + ".jpg";
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File imageFile  = new File(storageDir, imageFileName);
            cameraImageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", imageFile);
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            cameraLauncher.launch(cameraIntent);
        } catch (Exception e) {
            showError("Could not launch camera: " + e.getMessage());
        }
    }

    private void showLoadingState(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnChooseImage.setEnabled(!loading);
        binding.btnCaptureImage.setEnabled(!loading);
    }

    private void showError(@NonNull String message) {
        binding.tvResults.setText("⚠️ " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (imageLabeler != null) {
            imageLabeler.close();
        }
    }
}
