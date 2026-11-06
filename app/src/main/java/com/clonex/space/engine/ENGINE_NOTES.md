# CloneX Engine — v3.0 notes (NewBlackbox)

## Why v3.0 replaced v2.0

v2.0 used the MultiApp opensdk (HackApi) engine, frozen since 2023. On
the user's phone (OnePlus DN2101, Android 13) it installed clones fine
but its server REJECTED every launch (engine code 102), and sometimes
reported success while the clone's `:agent` process died at once.
v3.0 switches to NewBlackbox, which is actively maintained (Android
13–16), has a single boolean launch, and a 3-layer hidden-API bypass.

## What v3.0 does (real)

- Real virtual engine: **NewBlackbox (BlackBoxCore)** — a true sandbox
  container: native hooks, stub processes (`:p0`..`:pN`, `:black`
  server process), virtual PackageManager + hooked Binder/system
  services. No assist package, no moon.jar.
- `RealVirtualEngine` (engine/RealVirtualEngine.kt) implements the
  `VirtualEngine` interface over BlackBoxCore:
  - `installPackageAsUser(pkg, userId)` — clone an installed app
    (split APKs handled inside the engine by the MaxCloner patch)
  - `installPackageAsUser(File, userId)` — one APK file
  - `installApkFiles` — a directory of split APKs: base installed
    first, then the splits are copied into the engine's splits dir
  - `launchApk(pkg, userId): boolean` — one call, one boolean; no
    int codes, no 3-shot sequence
  - `uninstallPackageAsUser`, `clearPackage` (data), `stopPackage`
    (kill), `getInstalledApplications`, `isInstalled`
  - Google Suite: `installGms(userId)` / `uninstallGms(userId)` /
    `isInstallGms(userId)` — GMS-only, no Play Store app (it crashes
    inside NewBlackbox: SecurityException on LocaleManager)
  - Clear cache: the clone's data/cache dir is deleted directly
    (NewBlackbox has no cache-only API)
- `CloneXApplication` is a plain `Application`:
  `doAttachBaseContext(base, ClientConfiguration)` + `doCreate()`.
  Init runs in EVERY process of the APK.
- Split APKs: the MaxCloner split patch is applied to the Bcore source
  in CI (tools/patch-bcore-split.py) — the engine copies every split
  and extracts native libs from each split. See
  tools/bcore-split-patch/README.md.
- targetSdk **28** (the engine itself targets 28) — sideload-only,
  never Google Play.

## How the engine is built

The Bcore AAR is built in CI from the pinned upstream commit
(ALEX5402/NewBlackbox `89b59836c66f173756a4ae258cf379a957649820`),
patched with the split-APK script, then `:Bcore:assembleRelease`
with the repo's own Gradle wrapper (8.14.5, JDK 21, NDK
29.0.13846066, compileSdk 35). The AAR is copied to `app/libs/`.
See `.github/workflows/build.yml`.

## Licence

NewBlackbox is **Apache-2.0** (ALEX5402). The MaxCloner split patch
is also Apache-2.0. CloneX source stays public with attribution —
see README.md "Open-source attribution". No MultiApp artwork is
copied, and the opensdk (AGPL-3.0) is no longer used at all.

## Known limits (honest, unchanged)

- Banking / payment / UPI apps and apps declaring `REQUIRE_SECURE_ENV`
  detect virtual environments and refuse or ban. CloneX will never
  ship a bypass for that detection.
- Games with anti-cheat: cloning them risks an account ban.
- Play Store / GMS inside the sandbox is fragile (the Play Store app
  itself crashes inside NewBlackbox; GMS-only Suite is the working
  option).
- No root, no system modification, no silent installs.

## No 100% guarantee

No virtual-space app — including Parallel Space — runs 100% of apps.
Social / messaging apps (WhatsApp, Facebook, Instagram, Telegram) are
the realistic target. Compatibility is earned one app at a time, with
real phone testing.

## The old stub

`StubVirtualEngine.kt` is kept in the codebase, unused, as a reference
implementation of the `VirtualEngine` interface without a real engine.
