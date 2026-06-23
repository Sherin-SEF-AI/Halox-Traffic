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
    implementation(libs.coroutines.core)
    implementation(libs.okhttp) // model provisioning download
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // LiteRT (Google AI Edge) — runtime + GPU delegate. Classes remain under org.tensorflow.lite.*.
    // CONFIRM the artifact versions resolve, then confirm the YOLO26 export's output tensor layout.
    implementation(libs.litert)
    implementation(libs.litert.gpu)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}
