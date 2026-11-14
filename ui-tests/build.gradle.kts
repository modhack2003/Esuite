plugins { id("com.android.application") }
android {
    namespace = "dev.esuite.uitests"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    defaultConfig {
        applicationId = "dev.esuite.uitests"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "ci-only"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
// An independent, self-instrumenting APK: it does not depend on :app or share
// the app's library class loader. This harness is never a release asset.
dependencies {
    implementation("androidx.test:runner:1.6.2")
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("junit:junit:4.13.2")
}
