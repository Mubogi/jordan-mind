plugins {
    // Versions are declared once here and applied without version in the modules, which is what
    // Gradle requires when the same plugin is used by more than one subproject.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.desktop) apply false
}
