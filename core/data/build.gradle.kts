import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val cloudProperties = Properties().apply {
    val config = rootProject.file("sync.local.properties")
    if (config.exists()) config.inputStream().use { load(it) }
}

android {
    namespace = "com.trackermaster.core.data"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        listOf("api_key", "application_id", "project_id", "web_client_id", "owner_uid", "writer_key").forEach { key ->
            resValue("string", "cloud_$key", cloudProperties.getProperty(key, ""))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.work)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
