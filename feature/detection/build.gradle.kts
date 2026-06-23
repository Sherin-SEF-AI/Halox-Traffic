plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.haloxtraffic.feature.detection"
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
    implementation(project(":core:sensors"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.camerax.core) // ImageProxy in the analyzer + preprocessing path
    implementation(libs.coroutines.core)
    implementation(libs.okhttp) // model provisioning download
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Active detector: MediaPipe Tasks Vision ObjectDetector running a bundled EfficientDet-Lite0
    // (COCO) model — real on-device detection out of the box.
    implementation(libs.mediapipe.tasks.vision)

    // LiteRT path (kept for a custom YOLO26 export): runtime + GPU delegate, org.tensorflow.lite.*.
    implementation(libs.litert)
    implementation(libs.litert.gpu)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}
