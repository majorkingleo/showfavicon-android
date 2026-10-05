// Root build file: the plugins are only declared here, the :app module applies
// them. Keeping them unapplied at the root avoids duplicating versions.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
