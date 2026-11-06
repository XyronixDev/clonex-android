import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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

    // Declared only — no native .so is bundled in this MVP.
    // The real virtual engine (native hooking) is a later step; see
    // app/src/main/java/com/clonex/space/engine/ENGINE_NOTES.md
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.clonex.space"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0-mvp"
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
                // APK Signature Schemes enabled at build time.
                // The final artifact is (re-)signed and verified with
                // apksigner --v1/--v2/--v3/--v4 (see README "Release signing").
                v1SigningEnabled = true
                v2SigningEnabled = true
                v3SigningEnabled = true
                v4SigningEnabled = true
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

    kotlinOptions {
        jvmTarget = "17"
    }

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
}
