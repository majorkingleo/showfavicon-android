// Root build file: the application plugin is only declared here, the :app
// module applies it. Keeping it unapplied at the root avoids duplicating the
// version. There is no Kotlin plugin: Android Gradle Plugin 9 has built-in
// Kotlin support and brings its own Kotlin Gradle plugin.
plugins {
    alias(libs.plugins.android.application) apply false
}
