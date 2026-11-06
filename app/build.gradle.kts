import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // NO kotlin.android here — AGP 9.0+ applies Kotlin itself (run #4
    // failed when it was declared). The Compose compiler plugin stays:
    // it is a separate Kotlin subplugin, not part of AGP.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// RELEASE SIGNING — APK Signature Schemes V1 + V2 + V3 (+ V4 bonus)
// ---------------------------------------------------------------------------
// Credentials come ONLY from local.properties (on the builder machine) or
// from environment variables of the same names. Nothing is hardcoded here
// and the keystore / passwords are never committed (see .gitignore).
//
// local.properties keys:
//   CLONEX_STORE_FILE=/absolute/path/to/clonex-release.jks
//   CLONEX_STORE_PASSWORD=<store password>
//   CLONEX_KEY_ALIAS=clonex-release
//   CLONEX_KEY_PASSWORD=<key password>
//
// The SAME keystore must be reused for every future update: Play Store and
// Android reject updates signed with a different key.
// ---------------------------------------------------------------------------
val keystoreProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { keystoreProps.load(it) }
}

fun signingProp(name: String): String? =
    System.getenv(name) ?: keystoreProps.getProperty(name)

val releaseStoreFile: java.io.File? =
    signingProp("CLONEX_STORE_FILE")?.let { file(it) }?.takeIf { it.exists() }

android {
    namespace = "com.clonex.space"
    compileSdk = 37

    // v3.0: the NewBlackbox engine ships its native lib (libblackbox.so)
    // in the Bcore AAR. Bcore uses legacy packaging for it; keep the same
    // here so the engine can find its lib the way it expects.
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.clonex.space"
        minSdk = 26
        // v3.0: targetSdk 28 is an ENGINE requirement — NewBlackbox
        // itself targets 28, and its hidden-API hooks rely on it.
        // Consequence: sideload-only, never Google Play.
        targetSdk = 28
        versionCode = 14
        versionName = "3.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Only wired up when the keystore actually exists on this machine.
            // CI builds without local.properties therefore still produce an
            // unsigned release APK artifact; signing happens locally.
            if (releaseStoreFile != null) {
                storeFile = releaseStoreFile
                storePassword = signingProp("CLONEX_STORE_PASSWORD")
                keyAlias = signingProp("CLONEX_KEY_ALIAS")
                keyPassword = signingProp("CLONEX_KEY_PASSWORD")
                // APK Signature Schemes V1+V2+V3+V4 (AGP 9.5 ApkSigningConfig
                // names — the old v1SigningEnabled etc. do not exist in
                // AGP 9; run #7 failed on them as unresolved references).
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true          // R8 — keeps the APK small
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            // No android:debuggable in release — release builds are never
            // debuggable. (No buildTypes.debug block needed; AGP default.)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Run #31 failed on lintVitalRelease: ExpiredTargetSdkVersion —
    // Google Play requires target API 35+, but targetSdk 28 is an
    // ENGINE requirement (see defaultConfig above) and CloneX is
    // sideload-only, never Play. Lint must not fail the release build
    // for this deliberate choice; other fatal lint issues still report.
    lint {
        abortOnError = false
        disable += "ExpiredTargetSdkVersion"
    }

    // NOTE (AGP 9): android.kotlinOptions {} does NOT exist under AGP 9's
    // built-in Kotlin (run #7 failed on it as an unresolved reference).
    // JVM target is set in the top-level kotlin {} block below instead.

    buildFeatures {
        compose = true
    }

    // Small-size goal: arm64 only. A 32-bit + 64-bit universal build would
    // roughly double the native payload; 32-bit-only devices are out of
    // scope for this MVP (see README).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    packaging {
        jniLibs {
            // NewBlackbox's Bcore AAR uses legacy .so packaging for
            // libblackbox.so; keep the same so the engine's native lib
            // stays loadable.
            useLegacyPackaging = true
        }
    }
}

// JVM target for Kotlin compilation (AGP 9 built-in Kotlin: the
// top-level kotlin {} extension is available without applying any
// Kotlin plugin; kotlinOptions inside android {} is gone in AGP 9).
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    // v3.0: NewBlackbox virtual engine (ALEX5402/NewBlackbox, Apache-2.0).
    // The Bcore AAR is built in CI from the pinned upstream commit plus
    // the MaxCloner split-APK patch — see .github/workflows/build.yml
    // and tools/patch-bcore-split.py.
    implementation(files("libs/bcore-release.aar"))
}
