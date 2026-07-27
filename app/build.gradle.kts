plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.sbs"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sbs"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema export directory — commits generated schemas/ to git so Room
        // can validate every migration path at build time.
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.schemaLocation" to "$projectDir/schemas",
                    "room.incremental"    to "true"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // ── 16 KB page-size support ───────────────────────────────────────────────
    // Required for all new apps / updates submitted to Google Play targeting
    // Android 15+ devices after November 1st, 2025.
    // useLegacyPackaging = false tells the AGP to package .so files uncompressed
    // so the linker can enforce ELF segment alignment at 16 KB boundaries.
    // The MapLibre Native Android SDK 11.6.0+ was compiled with
    // -z max-page-size=16384 to satisfy this requirement.
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {

    // ── Room ─────────────────────────────────────────────────────────────────
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    // ── Hilt ─────────────────────────────────────────────────────────────────
    // (No Hilt in this project – using manual singleton pattern + EntryPoint)

    // ── WorkManager ──────────────────────────────────────────────────────────
    implementation("androidx.work:work-runtime:2.9.0")

    // ── Firebase ─────────────────────────────────────────────────────────────
    implementation(platform("com.google.firebase:firebase-bom:34.11.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.android.gms:play-services-auth:21.4.0")

    // ── MapLibre Native Android ───────────────────────────────────────────────
    // Updated to 11.8.6 which includes 16 KB ELF segment alignment support.
    // The tile source is switched from demotiles to OpenFreeMap Liberty
    // (Google-Maps-like look, completely free, no API key) and Esri World
    // Imagery for satellite view — both configured in DashboardActivity.java.
    // If 11.8.6 is unavailable in your repository use the latest 11.x.x tag.
    implementation("org.maplibre.gl:android-sdk:11.8.6")

    // ── UI ───────────────────────────────────────────────────────────────────
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-livedata:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")

    // ── Image loading ─────────────────────────────────────────────────────────
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // ── Testing ───────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
