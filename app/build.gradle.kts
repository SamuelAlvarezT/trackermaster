import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.process.CommandLineArgumentProvider

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

abstract class MockitoAgentArguments : CommandLineArgumentProvider {
    @get:Classpath
    abstract val agentFiles: ConfigurableFileCollection

    override fun asArguments(): Iterable<String> =
        listOf("-javaagent:${agentFiles.singleFile.absolutePath}", "-Xshare:off")
}

val mockitoAgent = configurations.create("mockitoAgent") {
    isCanBeConsumed = false
}

// Device validation uses a distinct sandbox; the regular build keeps its existing application ID.
val validationApplicationId=providers.gradleProperty("validationApplicationId").orNull?.also {
    require(it.startsWith("com.trackermaster.validation.") && it.matches(Regex("[a-zA-Z0-9_.]+")))
}

tasks.withType<Test>().configureEach {
    systemProperty("phase8.screenshots",rootProject.layout.projectDirectory.dir("docs/money/native/phase8-ui").asFile.absolutePath)
    jvmArgumentProviders.add(objects.newInstance(MockitoAgentArguments::class.java).apply {
        agentFiles.from(mockitoAgent)
    })
}

android {
    namespace = "com.trackermaster.app"
    compileSdk = 35

    defaultConfig {
        applicationId = validationApplicationId ?: "com.trackermaster.app"
        manifestPlaceholders["appLabel"]=if(validationApplicationId==null) "@string/app_name" else "Trackermaster · pruebas"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures { compose = true }
    // Keep EN/ES/ZH available offline when distributing an App Bundle.
    bundle { language { enableSplit = false } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:widgets"))
    implementation(project(":feature:habits"))
    implementation(project(":feature:mood"))
    implementation(project(":feature:expense"))
    implementation(project(":feature:focus"))
    implementation(project(":feature:journal"))
    implementation(project(":feature:insights"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:backup"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.work)
    implementation(libs.androidx.biometric)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.compiler.work)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.core)
    mockitoAgent(libs.mockito.core) { isTransitive = false }
    testImplementation(project(":core:domain"))
}
