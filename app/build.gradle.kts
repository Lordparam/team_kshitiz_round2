plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.campusnavpro"
    // Compile SDK 34 is the stable target for your current library versions
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.campusnavpro"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            // Minification must be handled with ProGuard rules to prevent crashing
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions { jvmTarget = "1.8" }

    buildFeatures { compose = true }

    composeOptions { kotlinCompilerExtensionVersion = "1.5.1" }

    packaging {
        resources {
            // Added extra excludes to fix the "Generate APK" error
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
        }
    }
}

dependencies {
    val camerax_version = "1.3.1"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")
    // 1. Map Core (Cleaned of duplicates)
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("com.google.maps.android:android-maps-utils:3.5.3")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("io.coil-kt:coil-compose:2.5.0")
    // 2. Compose & UI
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // 3. Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    // Standard TFLite
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    // Support library (Provides NormalizeOp and ImageProcessor)
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    // Task library (Optional, but good for vision tasks)
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")
    // GPU acceleration
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    // 4. Core Libraries
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
        // Replace the task-vision lines with these:
        implementation("org.tensorflow:tensorflow-lite:2.14.0")
        implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
        implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}