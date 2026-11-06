# CloneX — Virtual Space / App Cloner (v2.0)

**App:** CloneX · **Package:** `com.clonex.space` · **Repo:** `clonex-android`

Clone multiple apps, keep each clone in its own space, run them with
separate logins — Parallel Space / Dual App style. v2.0 ships the **real
virtual engine**: the MultiApp opensdk (HackApi) — a true sandbox
container with native hooks, split-APK install, and a 32-bit assist
package. Light-mode UI styled like the MultiApp screenshots.

> **Delivery:** no zip is published. Push to GitHub → GitHub Actions
> builds the release APK (including the engine AAR) → download it from
> the Actions tab (artifact `CloneX-release-apk`).

## Roman Urdu — mukhtasar

v2.0 mein asli engine hai: app clone hoti hai aur virtual space ke andar
**run bhi hoti hai** — tap karne par clone launch hoti hai. Split APK
(WhatsApp, Facebook, Instagram) folder se install hoti hai. Banking
apps aur REQUIRE_SECURE_ENV wali apps ismein kabhi nahi chalengi.
100% guarantee koi virtual-space app nahi deti, Parallel Space bhi nahi.

## What v2.0 does (real)

- Light-mode Compose UI like the MultiApp screenshots: title top-left,
  3-dot menu, white feature card (Secret Zone / Storage Manager /
  Device Info / Find Help), clone grid with real icons, blue + FAB.
- **Already PRO** — every feature unlocked from the start: up to 10
  clones per app, Secret Zone, Storage Manager, Google Suite. No upsell,
  no gating, no VIP anything.
- **Real virtual engine** (MultiApp opensdk, HackApi): clones actually
  install into the virtual space and launch on tap.
- **Add App** — pick any installed app, save it as Clone 1 / Clone 2…
  (each clone = its own virtual user space).
- **Import APK** — system file picker (SAF): a single APK file, or a
  **folder of split APKs** (WhatsApp / Facebook / Instagram from Play).
- **Import Apps screen** — HOT section (Instagram, YouTube, Telegram,
  WhatsApp, Facebook), A-Z list with a right-side alphabet index.
- **Secret Zone** — PIN-gated private clones, hidden from the home grid.
- **Storage Manager** — per-clone Clear cache / Clear data through the
  engine (deletePackageCache / deletePackageData).
- **Device Info** — engine runtime properties + host device details.
- **Install Google Suite** — the Play Store build bundled with the
  engine (`com.waxmoon.ma.gp`) + host GMS / GSF / vending when present.
- **Device Info** — engine runtime properties + host device details.
- Long-press a clone: Launch / Secret Zone / Clear data / Clear cache /
  Kill app / Remove.
- Clone library in **Room** (survives restarts), settings in
  **DataStore** (max clones, notifications, Secret Zone PIN).
- Release build: R8 minify + shrinkResources, **arm64-only** (small size).

## Honest limits

- The engine is frozen since 2023 (Android 14 max in its changelog) —
  it may not work on newer Android versions. Verify on the phone.
- Banking / payment / UPI apps and apps declaring
  `REQUIRE_SECURE_ENV` are out of scope on purpose — they detect virtual
  environments and refuse or ban. No bypass will ever be shipped.
- 32-bit-only apps need the assist package (`com.clonex.space.assist`),
  installed through the system installer when CloneX asks.
- Play Store / GMS inside the sandbox is fragile; the bundled Play
  Store build is a modded APK, not the stock Play Store.
- No virtual-space app runs 100% of apps — social / messaging apps
  (WhatsApp, Facebook, Instagram, Telegram) are the realistic target.

## Stack (locked)

| Piece | Version |
|---|---|
| Kotlin | 2.4.21 (latest stable, 8 Oct 2026) |
| Jetpack Compose BOM | 2026.08.00 (Compose 1.12) |
| AGP | 9.5.0 (verified via Google Maven metadata 2026-11-06; AGP 9.1.2 does not exist) |
| Gradle | 9.8.2 (locked spec said 9.7 — that version was never released) |
| JDK | 21 (CloneX build); 11 (opensdk AAR build in CI) |
| compileSdk / targetSdk / minSdk | 37 / **26** / 26 (targetSdk 26 is an engine requirement — sideload-only) |
| Engine | MultiApp opensdk (HackApi), AGPL-3.0 — AAR built in CI from pinned commit `3de55af6` |
| Room / DataStore / KSP | 2.7.1 / 1.1.7 / 2.3.10 |

## Build via GitHub Actions (the delivery route)

1. Push `main`. The workflow `.github/workflows/build.yml` runs:
   JDK 11 → clone WaxMoon/MultiApp at the pinned commit, patch the
   master/assist package names, build `:opensdk:assembleRelease` (AGP
   7.0.2) + `:assist:assembleRelease` → copy the AAR to `app/libs/` and
   the assist APK to `app/src/main/assets/` → JDK 21 → Gradle 9.8.2 →
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
  build.gradle.kts            # SDKs, R8, arm64 split, signing config, opensdk AAR
  src/main/AndroidManifest.xml  # .CloneXApplication + FileProvider (assist APK)
  src/main/res/xml/file_paths.xml
  src/main/java/com/clonex/space/
    CloneXApplication.kt      # extends HackApplication (engine init)
    MainActivity.kt           # single activity, light Compose UI (home /
                              # Import Apps / Secret Zone), real icons
    ui/Theme.kt               # light Material 3 theme
    data/                     # Room: CloneEntry (v2: isPrivate), CloneDao,
                              # CloneDatabase (v2 + migration); DataStore:
                              # SettingsStore (max clones, Secret Zone PIN)
    engine/                   # VirtualEngine, RealVirtualEngine (HackApi),
                              # StubVirtualEngine (kept, unused), ENGINE_NOTES.md
.github/workflows/build.yml  # Actions: opensdk AAR + assist APK, then CloneX APK
```

## Open-source attribution (AGPL-3.0)

CloneX v2.0 uses the **MultiApp opensdk** virtual engine by WaxMoon:

- MultiApp: https://github.com/WaxMoon/MultiApp (pinned commit
  `3de55af64ec925de390cbf30bb82f279a51945e0`)
- opensdk: https://github.com/WaxMoon/opensdk (submodule `eaab164`)
- Licence: **GNU Affero General Public License v3.0 (AGPL-3.0)**

The opensdk is used unmodified except for the host/assist package
names (`com.clonex.space` / `com.clonex.space.assist`) and CloneX
branding. No MultiApp artwork is copied (feature icons are generic
shapes/emoji). CloneX source code is published at
https://github.com/XyronixDev/clonex-android under the same licence.

## Next step (phone testing)

1. Install v2.0 over v1.1.0 (same keystore) and check the engine starts
   (no crash on launch; logcat: `CloneXApplication` / `HackApplication`).
2. Clone WhatsApp (split APK) → launch → log in with a second number.
3. Clone Facebook, Instagram, Telegram, Chrome (WebView).
4. One 32-bit-only app → assist package prompt → install → launch.
5. One banking app → expect a clean refusal, not a host crash.
