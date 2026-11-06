# CloneX Engine — v2.0 notes (MultiApp opensdk)

## What v2.0 does (real)

- Real virtual engine: **MultiApp opensdk (HackApi)** — a true sandbox
  container, not a Java dynamic proxy: native seccomp/bpf svc hooks,
  stub processes (`:agent.p0`..`:agent.pN`, `:core` service process),
  virtual PackageManager + hooked Binder/system services.
- `RealVirtualEngine` (engine/RealVirtualEngine.kt) implements the
  `VirtualEngine` interface with HackApi:
  - `installPackageFromHost(pkg, userId, false)` — clone an installed app
  - `installApkFiles(pathOrDir, userId, false)` — one APK, or a directory
    of split APKs (WhatsApp / Facebook / Instagram from Play)
  - `startActivity(getLaunchIntentForPackage(pkg, userId), userId)` —
    launch; `startPackage` (deprecated) is the fallback
  - `uninstallPackage`, `deletePackageData`, `deletePackageCache`,
    `killApplication`, `getInstalledPackages`, `getAvailableUser`
  - `getPackageSetting` "request_assistant" — 32-bit clones need the
    assist package (`com.clonex.space.assist`)
- `CloneXApplication` extends `HackApplication`: the engine extracts
  `moon.jar` from the AAR assets on first run and loads it with a
  DexClassLoader. Init runs in EVERY process of the APK.
- Google Suite: the Play Store build bundled with the engine AAR
  (`assets/com.waxmoon.ma.gp.apk`, package `com.waxmoon.ma.gp`) + the
  host's GMS / GSF / vending packages when present.
- targetSdk **26** (the engine itself targets 26) — sideload-only,
  never Google Play.

## How the engine is built

The opensdk AAR is built in CI from the pinned upstream commit
(WaxMoon/MultiApp `3de55af64ec925de390cbf30bb82f279a51945e0`, opensdk
submodule `eaab164`), with the root `build.gradle` ext block patched so
`MASTER_PACKAGE=com.clonex.space` and
`ASSIST_PACKAGE=com.clonex.space.assist` are baked into the AAR's
BuildConfig. The assist APK is built from the same checkout
(`:assist:assembleRelease`) and copied into `app/src/main/assets/`.
See `.github/workflows/build.yml`.

## Licence

The opensdk is **AGPL-3.0** (WaxMoon). CloneX source stays public under
AGPL-3.0 with attribution — see README.md "Open-source attribution".
The opensdk is used unmodified except for the host/assist package names
and CloneX branding. No MultiApp artwork is copied.

## Known limits (honest, unchanged)

- The engine is frozen since 2023 (Android 14 max in its changelog).
  It may not work on newer Android versions — verify on the phone.
- Banking / payment / UPI apps and apps declaring `REQUIRE_SECURE_ENV`
  detect virtual environments and refuse or ban. Parallel Space itself
  dropped support for those apps in 4.0.9544. CloneX will never ship a
  bypass for that detection.
- Games with anti-cheat: cloning them risks an account ban.
- Play Store / GMS inside the sandbox is fragile (the engine's own
  Play Store build is a modded APK, not the stock Play Store).
- 32-bit-only clones need the assist package installed on the phone.
- No root, no system modification, no silent installs.

## No 100% guarantee

No virtual-space app — including Parallel Space — runs 100% of apps.
Social / messaging apps (WhatsApp, Facebook, Instagram, Telegram) are
the realistic target. Compatibility is earned one app at a time, with
real phone testing.

## The old stub

`StubVirtualEngine.kt` is kept in the codebase, unused, as a reference
implementation of the `VirtualEngine` interface without a real engine.
