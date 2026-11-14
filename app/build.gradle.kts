import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val releaseVersion = Properties().apply { rootProject.file("release/version.properties").inputStream().use(::load) }
val signingNames = listOf("ESUITE_KEYSTORE_PATH", "ESUITE_STORE_PASSWORD", "ESUITE_KEY_ALIAS", "ESUITE_KEY_PASSWORD")
val signingValues = signingNames.associateWith { providers.environmentVariable(it).orNull }
require(signingValues.values.all { it.isNullOrBlank() } || signingValues.values.all { !it.isNullOrBlank() }) {
    "Release signing requires all four ESUITE signing environment variables. No debug-key fallback is permitted."
}

android {
    namespace = "dev.esuite.app"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    defaultConfig {
        applicationId = "dev.esuite.app"
        minSdk = 28
        targetSdk = 37
        versionCode = releaseVersion.getProperty("versionCode").toInt()
        versionName = releaseVersion.getProperty("versionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        if (signingValues.values.all { !it.isNullOrBlank() }) create("production") {
            storeFile = file(signingValues.getValue("ESUITE_KEYSTORE_PATH")!!)
            storePassword = signingValues.getValue("ESUITE_STORE_PASSWORD")
            keyAlias = signingValues.getValue("ESUITE_KEY_ALIAS")
            keyPassword = signingValues.getValue("ESUITE_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("production")
        }
    }
    testBuildType = "debug"
    lint { abortOnError = true; checkReleaseBuilds = true }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.webkit:webkit:1.12.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
