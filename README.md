# CloneX — Virtual Space / App Cloner (v3.0)

**App:** CloneX · **Package:** `com.clonex.space` · **Repo:** `clonex-android`

Clone multiple apps, keep each clone in its own space, run them with
separate logins — Parallel Space / Dual App style. v3.0 ships the
**real virtual engine**: NewBlackbox (BlackBoxCore) — a true sandbox
container with native hooks, split-APK install (Instagram, Facebook,
WhatsApp), and a single boolean launch. Light-mode launcher-style UI.

> **Delivery:** no zip is published. Push to GitHub → GitHub Actions
> builds the release APK (including the engine AAR) → download it from
> the Actions tab (artifact `CloneX-release-apk`).

## Roman Urdu — mukhtasar

v3.0 mein naya engine hai: NewBlackbox. v2.0 ka MultiApp opensdk
engine har clone launch reject kar deta tha (engine code 102) — is
liye engine badla gaya. App clone hoti hai aur virtual space ke andar
**run bhi hoti hai** — tap karne par clone launch hoti hai. Split APK
(WhatsApp, Facebook, Instagram) folder se install hoti hai. Banking
apps aur REQUIRE_SECURE_ENV wali apps ismein kabhi nahi chalengi.
100% guarantee koi virtual-space app nahi deti, Parallel Space bhi nahi.

## What v3.0 does (real)

- Light-mode Compose UI: blank home (clone grid + blue + FAB), launcher-
  style tiles (real icon + name, no card), Import Apps screen (HOT
  section, A-Z list with a right-side alphabet index).
- **Already PRO** — every feature unlocked from the start: up to 10
  clones per app, Google Suite. No upsell, no gating, no VIP anything.
- **Real virtual engine** (NewBlackbox, BlackBoxCore): clones actually
  install into the virtual space and launch on tap.
- **Add App** — pick any installed app, save it as Clone 1 / Clone 2…
  (each clone = its own virtual user space).
- **Import APK** — system file picker (SAF): a single APK file, or a
  **folder of split APKs** (WhatsApp / Facebook / Instagram from Play).
- **Storage** — per-clone Clear cache / Clear data through the engine.
- **Device Info** — engine status + host device details + last crash.
- **Install Google Suite** — the engine's own GMS install (gms + gsf).
  The Play Store app itself does not work inside the space.
- Long-press a clone: Launch / Add another clone / Clear data /
  Clear cache / Kill app / Remove / Remove from library only.
- Clone library in **Room** (survives restarts), settings in
  **DataStore** (max clones, notifications).
- Release build: R8 minify + shrinkResources, **arm64-only** (small size).

## Honest limits

- Banking / payment / UPI apps and apps declaring
  `REQUIRE_SECURE_ENV` are out of scope on purpose — they detect virtual
  environments and refuse or ban. No bypass will ever be shipped.
- Play Store / GMS inside the sandbox is fragile; the Play Store app
  itself crashes inside NewBlackbox, so the Suite is GMS-only.
- No virtual-space app runs 100% of apps — social / messaging apps
  (WhatsApp, Facebook, Instagram, Telegram) are the realistic target.

## Stack (locked)

| Piece | Version |
|---|---|
| Kotlin | 2.4.21 (latest stable, 8 Oct 2026) |
| Jetpack Compose BOM | 2026.08.00 (Compose 1.12) |
| AGP | 9.5.0 (verified via Google Maven metadata 2026-11-06; AGP 9.1.2 does not exist) |
| Gradle | 9.8.2 (locked spec said 9.7 — that version was never released) |
| JDK | 21 (CloneX build); 21 (Bcore AAR build in CI, via NewBlackbox's own wrapper) |
| compileSdk / targetSdk / minSdk | 37 / **28** / 26 (targetSdk 28 is an engine requirement — sideload-only) |
| Engine | NewBlackbox (BlackBoxCore), Apache-2.0 — AAR built in CI from pinned commit `89b59836` + MaxCloner split patch |
| Room / DataStore / KSP | 2.7.1 / 1.1.7 / 2.3.10 |

## Build via GitHub Actions (the delivery route)

1. Push `main`. The workflow `.github/workflows/build.yml` runs:
   JDK 21 → clone ALEX5402/NewBlackbox at the pinned commit, apply
   the split-APK patch (`tools/patch-bcore-split.py`), build
   `:Bcore:assembleRelease` (Gradle 8.14.5, NDK 29.0.13846066,
   compileSdk 35) → copy the AAR to `app/libs/` → Gradle 9.8.2 →
   `assembleRelease` → `apksigner verify`.
2. Download the APK from the run's artifacts (`CloneX-release-apk`).

> The workflow file itself cannot be pushed through the GitHub API
> (403) — it is uploaded through the browser UI. Code files are pushed
> normally; the workflow file on disk must match what is on GitHub.

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
  build.gradle.kts            # SDKs, R8, arm64 split, signing config, Bcore AAR
  src/main/AndroidManifest.xml  # .CloneXApplication (plain Application)
  src/main/java/com/clonex/space/
    CloneXApplication.kt      # plain Application (BlackBoxCore init)
    MainActivity.kt           # single activity, light Compose UI (home /
                              # Import Apps), real icons
    ui/Theme.kt               # light Material 3 theme
    data/                     # Room: CloneEntry, CloneDao, CloneDatabase;
                              # DataStore: SettingsStore (max clones)
    engine/                   # VirtualEngine, RealVirtualEngine (BlackBoxCore),
                              # StubVirtualEngine (kept, unused), ENGINE_NOTES.md
tools/
  patch-bcore-split.py        # applies the MaxCloner split-APK patch to a
  bcore-split-patch/          #   NewBlackbox checkout (4 patched Java files)
.github/workflows/build.yml  # Actions: Bcore AAR (patched), then CloneX APK
```

## Open-source attribution (Apache-2.0)

CloneX v3.0 uses the **NewBlackbox** virtual engine by ALEX5402:

- NewBlackbox: https://github.com/ALEX5402/NewBlackbox (pinned commit
  `89b59836c66f173756a4ae258cf379a957649820`)
- Licence: **Apache License 2.0**

Split-APK support comes from the MaxCloner fork's patch:

- MaxCloner: https://github.com/ashiquesheriefofficial-max/MaxCloner
- Licence: **Apache License 2.0**

CloneX source code is published at
https://github.com/XyronixDev/clonex-android. No MultiApp artwork is
copied (feature icons are generic shapes). The MultiApp opensdk
(AGPL-3.0) was used in v2.0–v2.0.10 and is no longer part of CloneX.

## Next step (phone testing)

1. Install v3.0.0 fresh (old opensdk clones are stale — Remove + re-add
   them) and check the engine starts (no crash on launch).
2. Clone Instagram (split APK) → tap the clone → it must OPEN (this is
   the v3.0.0 test gate: v2.0.10 never opened a clone).
3. Clone WhatsApp, Facebook, Telegram, Chrome (WebView).
4. One banking app → expect a clean refusal, not a host crash.
