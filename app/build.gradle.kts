plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.jedl.videoprogen"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.jedl.videoprogen"
        minSdk = 26
        targetSdk = 34
        versionCode = 17
        versionName = "0.4.9"
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
    signingConfigs {
        create("jedl") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "jedl2026"
            keyAlias = "jedl"
            keyPassword = "jedl2026"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("jedl") }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // El modelo CartoonGAN (3 MB) y los de MediaPipe van en assets, no comprimir:
    // mmap directo sin gastar RAM extra
    androidResources {
        noCompress += listOf("tflite", "task")
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    // Recorte con IA ML Kit Subject Segmentation (unbundled: el modelo lo baja Google Play services la 1ra vez)
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
    // Estilo anime real: TensorFlow Lite (Apache 2.0) para el CartoonGAN .tflite
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Dependencias de Jetpack Compose (Diseño Profesional)
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui:1.6.1")
    implementation("androidx.compose.ui:ui-graphics:1.6.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.1")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.compose.material:material-icons-extended:1.6.1")
}
