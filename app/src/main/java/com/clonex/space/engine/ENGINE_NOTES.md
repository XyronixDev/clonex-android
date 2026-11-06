# CloneX Engine — honest notes (MVP)

## What this MVP does

- Premium Compose UI, clone library (Room), settings (DataStore).
- Add an installed app to the library; import an APK file via the system
  file picker (SAF) and read its real package name / label with
  `PackageManager`.
- The entry is stored and survives restarts. Tapping a clone shows, in
  plain words, that virtual launch is not implemented yet.

## What it does NOT do

- It does **not** install an APK into a virtual space.
- It does **not** launch an app inside a virtual space.
- It does **not** isolate data between clones.
- Nothing here is faked: `StubVirtualEngine.launchApp()` returns
  `NotImplemented` and the UI shows that message.

## What the real engine needs

A virtual space is a partial Android runtime: a virtual PackageManager,
hooked Binder/system-service calls, IO redirection to the virtual space,
and stub processes that host the cloned app. In practice that means:

1. A maintained VirtualApp fork (the original open-source VirtualApp
   stopped updating in Dec 2017; its business version is paid — do not
   copy the app module to a store build, check its licence first).
   A community-maintained fork that tracks newer Android versions is the
   realistic base.
2. Native (C/C++ NDK) libs for hooking + IO redirection, built for
   arm64-v8a (and armv7 if 32-bit apps must be supported — that doubles
   the native payload and the APK size).
3. Per-Android-version testing: every Android release changes hidden APIs,
   so the engine needs a fix pass per targetSdk bump.
4. `ndkVersion` is already declared in `app/build.gradle.kts`; no `.so`
   is bundled in this MVP.

## Out of scope — on purpose

- Banking / payment / UPI apps and any app declaring
  `REQUIRE_SECURE_ENV`: they detect virtual environments and refuse or
  ban. Parallel Space itself dropped support for those apps in 4.0.9544.
  CloneX will never ship a bypass for that detection.
- Games with anti-cheat: cloning them risks an account ban.
- No root, no system modification, no silent installs.

## No 100% guarantee

No virtual-space app — including Parallel Space — runs 100% of apps.
Social / messaging apps (WhatsApp, Facebook, Instagram, Telegram) are
the realistic target. The engine step is where that compatibility is
earned, one app at a time, with real phone testing.
