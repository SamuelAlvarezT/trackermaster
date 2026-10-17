plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.trackermaster.core.database"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
    sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// Include a newly generated schema in the same build that first creates it.
tasks.matching { it.name == "mergeDebugAssets" || it.name == "mergeDebugUnitTestAssets" }.configureEach {
    dependsOn("kspDebugKotlin")
}
