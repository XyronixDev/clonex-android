// CloneX — root build file.
// Versions are pinned in gradle/libs.versions.toml (single source of truth).
// NOTE (run #4, AGP 9.5): AGP 9.0+ applies the Kotlin Gradle plugin
// itself — declaring org.jetbrains.kotlin.android here (or in the app
// module) makes the build FAIL ("plugin is no longer required").
// So kotlin.android is NOT listed here; only the Compose compiler
// plugin (a separate Kotlin subplugin) and KSP are declared.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
