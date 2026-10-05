plugins {
    // Kotlin is compiled by AGP itself (built-in Kotlin since AGP 9), so there
    // is no org.jetbrains.kotlin.android plugin to apply here.
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.martin.showfavicon"
    // API 37.2, not 36: androidx.core 1.19.1 refuses to be consumed by a project
    // that compiles against an older API level. Minor versions are a separate
    // property, hence compileSdkMinor.
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.martin.showfavicon"
        // Android 8.0: the oldest release with adaptive icons and the
        // notification widget APIs this app relies on.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // No shrinking yet: the app has no reflection and only a handful of
            // classes, so minification buys nothing but risk at this stage.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        // Kotlin's jvmTarget follows this value with built-in Kotlin, so it does
        // not need a second setting.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.material)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
