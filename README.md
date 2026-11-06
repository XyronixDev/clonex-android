# CloneX — Virtual Space / App Cloner (MVP)

**App:** CloneX · **Package:** `com.clonex.space` · **Repo:** `clonex-android`

Clone multiple apps, keep each clone in its own space, run them with
separate logins — Parallel Space / Dual App style. This repository holds
the **MVP scaffold**: premium UI + clone library + APK import. The real
virtual engine (which actually runs a cloned app inside a virtual space)
is the next step and is **not** bundled yet — see "Honest limits" below.

> **Delivery:** no zip is published. Push to GitHub → GitHub Actions
> builds the release APK → download it from the Actions tab
> (artifact `CloneX-release-apk`).

## Roman Urdu — mukhtasar

Ye MVP hai: UI premium hai, clone ki list save hoti hai, APK import
hota hai, lekin abhi koi app virtual space ke andar **run nahi hoti** —
tap karne par saaf bata diya jata hai ke engine abhi nahi hai. Banking
apps aur REQUIRE_SECURE_ENV wali apps ismein kabhi nahi chalengi.
100% guarantee koi virtual-space app nahi deti, Parallel Space bhi nahi.
Asal engine agla step hai (VirtualApp fork + native libs).

## What the MVP does (real)

- Premium dark Compose UI: header, search, 3-column clone grid.
- **Add App** — pick any installed app, save it as Clone 1 / Clone 2…
- **Import APK** — system file picker (SAF), APK is copied to app
  storage, real package name + label are read with `PackageManager`.
- Clone library in **Room** (survives restarts), settings in
  **DataStore** (max clones per app, notifications on/off).
- Long-press a clone to remove it.
- Release build: R8 minify + shrinkResources, **arm64-only** (small size).

## What it does NOT do yet (honest)

- No virtual install, no virtual launch, no data isolation between
  clones. `StubVirtualEngine.launchApp()` returns `NotImplemented` and
  the UI shows that message — nothing is faked.
- Needs the real engine: a maintained VirtualApp fork + C/C++ NDK native
  libs for hooking / IO redirection. See
  `app/src/main/java/com/clonex/space/engine/ENGINE_NOTES.md`.
- Banking / payment / UPI apps and apps declaring
  `REQUIRE_SECURE_ENV` are out of scope on purpose — they detect virtual
  environments and refuse or ban. No bypass will ever be shipped.

## Stack (locked)

| Piece | Version |
|---|---|
| Kotlin | 2.4.21 (latest stable, 8 Oct 2026) |
| Jetpack Compose BOM | 2026.08.00 (Compose 1.12) |
| AGP | 9.1.2 (needs Gradle 9.3.1+) |
| Gradle | 9.8.2 (locked spec said 9.7 — that version was never released; 9.8.2 is the verified current stable per services.gradle.org, and it satisfies AGP 9.1's Gradle 9.3.1 minimum) |
| JDK | 21 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 26 |
| NDK | 27.2.12479018 (declared only, no `.so` bundled yet) |
| Room / DataStore / KSP | 2.7.1 / 1.1.7 / 2.3.10 |

## Build via GitHub Actions (the delivery route)

1. Create the repo **`clonex-android`** on GitHub (private is fine).
   The GitHub App cannot create repos (403) — create it in the browser,
   then the connector pushes this folder.
2. Push `main`. The workflow `.github/workflows/build.yml` runs:
   JDK 21 → Gradle 9.7 → `assembleRelease` → `apksigner verify`.
3. Download the APK from the run's artifacts (`CloneX-release-apk`).

Local build (needs Android SDK with platform 37.0 from the preview
channel + JDK 21):

```bash
sdkmanager --channel=3 "platforms;android-37.0" "build-tools;37.0.0"
# AGP resolves compileSdk 37 from the android-37.0 folder; if your AGP
# looks for android-37, symlink it:
#   ln -s android-37.0 $ANDROID_HOME/platforms/android-37
./gradlew assembleRelease
# unsigned APK: app/build/outputs/apk/release/app-arm64-v8a-release-unsigned.apk
```

## Release signing — V1 + V2 + V3 (+ V4 bonus)

`app/build.gradle.kts` defines `signingConfigs.release`, reading
credentials **only** from `local.properties` or env vars of the same
names. Nothing is hardcoded, nothing is committed (`.gitignore`
blocks `*.jks`, `*.keystore`, `local.properties`).

Local signing:

```bash
# 1. Generate the keystore ONCE (validity 25 years, alias clonex-release):
keytool -genkeypair -v -keystore clonex-release.jks -alias clonex-release \
  -keyalg RSA -keysize 4096 -validity 9125 \
  -dname "CN=CloneX, OU=CloneX, O=CloneX, L=Kathmandu, C=NP"

# 2. Put the passwords in local.properties (never in git):
#    CLONEX_STORE_FILE=/absolute/path/to/clonex-release.jks
#    CLONEX_STORE_PASSWORD=<store password>
#    CLONEX_KEY_ALIAS=clonex-release
#    CLONEX_KEY_PASSWORD=<key password>

# 3. Build (Gradle signs V1+V2+V3+V4) and verify:
./gradlew assembleRelease
apksigner verify --verbose app/build/outputs/apk/release/*.apk
# Must show: Verified using v1 scheme (JAR signing): true
#             Verified using v2 scheme (APK Signature Scheme v2): true
#             Verified using v3 scheme (APK Signature Scheme v3): true
#             Verified using v4 scheme (APK Signature Scheme v4): true
# V4 lives in a separate .apk.idsig file, not inside the APK.
```

GitHub Actions signing — add these 4 repository Secrets
(Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 clonex-release.jks` |
| `CLONEX_STORE_PASSWORD` | keystore password |
| `CLONEX_KEY_ALIAS` | `clonex-release` |
| `CLONEX_KEY_PASSWORD` | key password |

Without the secrets the workflow still builds and uploads an
**unsigned** release APK.

> **Same keystore forever:** every future update must be signed with
> this exact keystore. If it is lost, Play Store / Android will reject
> updates and the app must be published as a new app. Back it up offline.

## Project layout

```
settings.gradle.kts / build.gradle.kts / gradle/libs.versions.toml
app/
  build.gradle.kts            # SDKs, R8, arm64 split, signing config
  src/main/AndroidManifest.xml
  src/main/java/com/clonex/space/
    MainActivity.kt           # single activity, Compose UI
    ui/Theme.kt               # dark Material 3 theme
    data/                     # Room: CloneEntry, CloneDao, CloneDatabase
                              # DataStore: SettingsStore
    engine/                   # VirtualEngine, StubVirtualEngine, ENGINE_NOTES.md
.github/workflows/build.yml  # Actions: build + verify + upload APK
```

## Next step (real engine)

1. Pick a maintained VirtualApp fork that tracks Android 14/15/16,
   check its licence (the original is business-licensed for store use).
2. Add its native libs (arm64-v8a), wire `VirtualEngine` to it, keep
   the stub as fallback.
3. Phone-test with WhatsApp / Facebook / Instagram clones first;
   banking apps stay out of scope.
