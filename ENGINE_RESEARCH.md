# CloneX real-engine research (2026-11-06)

## Verdict
- Upstream `FBlackBox/BlackBox` is DEAD (dissolved, last commit ~3 years ago, Android 12 max).
  Do not use it.
- Maintained engine: **`ALEX5402/NewBlackbox`** (Apache 2.0, active into 2026,
  Android 14/15/16 support, releases ship Bcore AARs). Repo:
  https://github.com/ALEX5402/NewBlackbox
- Split-APK fork to evaluate as the base instead: **`MaxCloner`**
  (ashiquesheriefofficial-max/maxcloner, Apache 2.0) — adds split-APK
  (App Bundle) support. WhatsApp / Facebook / Instagram from Play are split
  APKs; plain NewBlackbox installs only base.apk and can crash. Must-test item.
- Other forks: Twoyi archived 2023 (dead); SandVXposed armeabi-v7a only +
  non-commercial licence (dead for modern phones); ZiTanIOI fork untested
  ("learning only"); ZENINXOP/ZCore paywalls Android 15+ (avoid).

## Legal cloud (honest note)
Licence is Apache 2.0 (can ship, including commercially, with attribution +
NOTICE + state changes). BUT upstream died after the VirtualApp copyright
holder (LuoHe) threatened VirtualApp-derived projects (issue #122). Residual
IP cloud, not a technical block. Keep attribution intact.

## Integration route — prebuilt AAR, NOT source modules
1. Build the AAR once: clone NewBlackbox (or MaxCloner), run
   `./gradlew :Bcore:assembleRelease` (JDK 21, NDK 29.0.13846066, AGP 8.13.2).
   Copy `Bcore/build/outputs/aar/Bcore-release.aar` -> `clonex-android/app/libs/`
   and commit it (reproducible; do not re-download per build).
2. `settings.gradle.kts`: add `maven { url = uri("https://jitpack.io") }`
   (FreeReflection resolves only from JitPack).
3. `app/build.gradle.kts`:
   ```kotlin
   implementation(files("libs/Bcore-release.aar"))
   implementation("com.github.tiann:FreeReflection:3.2.2")
   implementation("com.moandjiezana.toml:toml4j:0.7.2")
   ```
   Keep arm64-v8a ABI filter. If engine .so loading fails, add
   `packaging { jniLibs { useLegacyPackaging = true } }`.
4. Do NOT rename the engine package `top.niunaijun.blackbox` (stub processes,
   sandbox paths keyed to it). CloneX stays `com.clonex.space`.
5. No manual stub entries: Bcore's manifest declares ~355 stub components
   (ProxyActivity/Service/Provider/JobService/BroadcastReceiver across
   `:black` and `:p0`..`:pN`) — they merge from the AAR. Verify merged
   manifest after first build (`manifest-merger-*-report.txt`): `:black`
   service + `:pN` stub processes present.

## Application init (CloneXApplication.kt, android:name in manifest)
```kotlin
override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    try {
        BlackBoxCore.get().doAttachBaseContext(base, object : ClientConfiguration() {
            override fun getHostPackageName(): String = base.packageName
        })
    } catch (e: Exception) { e.printStackTrace() }
}
override fun onCreate() {
    super.onCreate()
    BlackBoxCore.get().doCreate()
}
```
Runs in EVERY process of the host APK. Do not reorder engine startup.

## APIs (BlackBoxCore.get(), all top.niunaijun.blackbox, userId = 0 always)
- `installPackageAsUser(packageName: String, userId: Int): InstallResult` — clone installed app
- `installPackageAsUser(apk: File, userId: Int)` / `(uri: Uri, userId: Int)` — APK import
- `launchApk(packageName: String, userId: Int): Boolean`
- `isInstalled(packageName: String, userId: Int): Boolean`
- `getInstalledApplications(flags: Int, userId: Int): List<ApplicationInfo>`
- `getInstalledPackages(flags: Int, userId: Int): List<PackageInfo>`
- `uninstallPackageAsUser(packageName: String, userId: Int)` / `uninstallPackage(packageName: String)`
- `clearPackage(packageName, userId)` / `stopPackage(packageName, userId)`
- `getUsers() / createUser(userId) / deleteUser(userId)` — use 0
- `isSupportGms() / isInstallGms(userId) / installGms(0)` — GMS fragile (see limits)
- `InstallResult`: `isSuccess()`, `getPackageName()`, `getErrorMessage()`

## Hard limits (do not walk back)
- **targetSdk 28 is mandatory in practice.** Engine gotcha #1: raising targetSdk
  breaks hidden-API access and hooks. CloneX is targetSdk 36 — keeping it will
  very likely break the engine. Consequence: **sideload-only, never Google Play.**
  This is the single biggest product decision — user must accept before building.
- Banking / UPI / REQUIRE_SECURE_ENV / strong Play Integrity apps will refuse
  to run (hardware-backed attestation cannot be faked). Anti-detection exists
  but is not a bypass. Out of scope.
- Play Store / GMS inside sandbox is fragile (open issue #37, May 2026:
  Play Store crashes with SecurityException setApplicationLocales).
- Split APKs not handled by plain NewBlackbox (see MaxCloner).
- arm64-only host: clones with 32-bit-only native libs fail in the 64-bit stub
  process. Most modern apps fine.
- No automated tests in the engine. Verify by build + run on device + logcat
  (`adb logcat | grep -iE "blackbox|BlackBoxCore|BActivityThread|HookManager"`).
- Storage: >= 2 GB free; every clone duplicates APK + data under host data dir.
- No root required.
- AGP conflict: engine built with AGP 8.13.2 / compileSdk 35 / NDK 29. AAR route
  avoids rebuilding the engine, so CloneX can stay AGP 9.5.0 — but do NOT try
  to recompile Bcore sources under AGP 9.5 (AIDL workaround, ndk-build,
  useLegacyPackaging).

## Work-profile alternative (Island/Shelter style)
OS-managed work profile via DevicePolicyManager: real OS isolation, banking /
Play Integrity apps WORK, Play-policy clean. Limits: only ONE work profile per
device -> only ONE extra copy of each app (no 2nd/3rd clone), briefcase badge,
no fake GPS/device spoofing, conflicts with employer MDM. Cannot deliver the
multi-clone Parallel Space-style product. Verdict: BlackBox for multi-clone
in-app experience; work profile for compatibility/security.

## Integration plan (ordered, after targetSdk-28 decision)
1. User accepts targetSdk 28 + sideload-only. Set `targetSdk = 28`
   (keep compileSdk 37, minSdk 26).
2. Evaluate MaxCloner first (split-APK fix), else NewBlackbox. Build AAR,
   copy to app/libs/, commit.
3. settings.gradle.kts: JitPack repo.
4. app/build.gradle.kts: AAR + FreeReflection + toml4j; arm64-v8a filter.
5. AndroidManifest: android:name=".CloneXApplication"; keep permissions;
   verify merged manifest.
6. New CloneXApplication.kt (init above).
7. New engine/RealVirtualEngine.kt implementing existing VirtualEngine
   interface with BlackBoxCore (all userId = 0). Keep stub behind a flag
   until first launch works on-device.
8. Wire MainActivity: Add App -> install-by-package-name; Import APK ->
   install-by-File; tap clone -> launchApk; long-press -> uninstall.
   Keep Room as UI cache, engine as source of truth.
9. GitHub Actions: no NDK needed (AAR prebuilt); JDK 21 + AGP 9.5.0.
10. Device test matrix: WhatsApp (split APK), Facebook, Instagram, Telegram,
    Chrome (WebView), one banking app (expect clean refusal, not host crash).
    Test Android 14/15 if possible.

## v2.0 update (2026-11-06) — engine decision changed

The NewBlackbox / MaxCloner route above is **superseded**. The user
ordered (2026-11-06 18:05) adoption of **ALL** plus points of
WaxMoon/MultiApp, engine included. CloneX v2.0 therefore uses the
**MultiApp opensdk (HackApi)** as its real virtual engine:

- Engine: WaxMoon/MultiApp, pinned commit
  `3de55af64ec925de390cbf30bb82f279a51945e0` (opensdk submodule `eaab164`),
  **AGPL-3.0**. CloneX source stays public under AGPL-3.0 with
  attribution (see README.md "Open-source attribution").
- Integration: the opensdk AAR is built in CI (`:opensdk:assembleRelease`,
  JDK 11, AGP 7.0.2, Gradle 7.0.2) with the root `build.gradle` ext block
  patched so `MASTER_PACKAGE=com.clonex.space` and
  `ASSIST_PACKAGE=com.clonex.space.assist` are baked into the AAR's
  BuildConfig; the assist APK is built from the same checkout
  (`:assist:assembleRelease`) and bundled as `assets/assist.apk`.
  See `.github/workflows/build.yml` and
  `app/src/main/java/com/clonex/space/engine/ENGINE_NOTES.md`.
- **targetSdk 26** (the engine itself targets 26) — sideload-only, never
  Google Play. This replaces the earlier targetSdk-28 decision question.
- Adopted plus points: true sandbox (no Java proxy, seccomp/bpf hooks),
  split-APK install, Play Services in container, master+assist packages,
  lifecycle callbacks + killApplication, device-info faking (engine-side),
  Private Space (Secret Zone), Storage Manager (per-clone Clear data /
  Clear cache), simple 3-call API surface.
- Known risk, unchanged: the engine is frozen since 2023 (Android 14
  max) — verify on the phone. If it fails on newer Android, the
  NewBlackbox / MaxCloner research above is the fallback.
- The NewBlackbox / MaxCloner research below is kept as fallback
  research only — do not start a second engine integration without a
  user decision.
- MaxCloner v4.1.0 release (2 Aug 2026) ships ONLY APKs
  (arm64-v8a 4.9 MB, armeabi-v7a, universal) — NO prebuilt Bcore AAR.
  The AAR must be built from source: clone the repo,
  `./gradlew :Bcore:assembleRelease` (JDK 21, NDK 29.0.13846066, AGP 8.13.2).
  Same presumably for NewBlackbox releases — verify when building.
- Size reference: a full engine host app (MaxCloner arm64 APK) is ~4.9 MB.

## Sources (read 2026-11-06)
- https://github.com/FBlackBox/BlackBox (dissolution notice)
- https://github.com/ALEX5402/NewBlackbox (README, releases, Docs.md, CLAUDE.md)
- https://github.com/ashiquesheriefofficial-max/maxcloner (split-APK fork)
- https://github.com/zitanioi/blackbox, https://github.com/twoyi/twoyi,
  https://github.com/asLody/SandVXposed
