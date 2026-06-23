plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.haloxtraffic.feature.anpr"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = libs.versions.javaTarget.get() }
}

dependencies {
    implementation(project(":core:model"))

    implementation(libs.coroutines.core)
    implementation(libs.timber)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // TODO(Phase 4): bring PaddleOCR PP-OCRv5 runtime online (Paddle-Lite or ONNX/LiteRT) after
    // confirming the export path that runs reliably across tiers.

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
