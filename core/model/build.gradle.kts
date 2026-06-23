plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    // Qualifier annotations shared across modules (e.g. @IoDispatcher); providers live in :app.
    api("javax.inject:javax.inject:1")

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
