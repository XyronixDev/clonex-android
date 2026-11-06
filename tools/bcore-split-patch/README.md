# Bcore split-APK patch (NewBlackbox)

Upstream NewBlackbox cannot install split APKs (App Bundles): it clones
only `base.apk`, so modern Play apps (Instagram, Facebook, WhatsApp)
install without native libraries and cannot run (upstream issue #37,
"getPackageArchiveInfo error").

These 4 files are the split-APK support from the MaxCloner fork
(https://github.com/ashiquesheriefofficial-max/MaxCloner, Apache-2.0),
which solved it. They are verbatim copies of MaxCloner's versions of
these files (no MaxCloner branding inside them; verified by diff
against upstream NewBlackbox HEAD 89b59836 — the ONLY differences are
the split changes below).

What they do:
- `BEnvironment.java` — `splits/` dir per package + `getInstalledSplitPaths()`
- `CopyExecutor.java` — copies every split; extracts native libs from EACH split, not just base
- `PackageManagerCompat.java` — populates `ApplicationInfo.splitSourceDirs` / `splitPublicSourceDirs`
- `BActivityThread.java` — class-loader dexPath = base + all splits; passes `nativeLibraryDir`

`tools/patch-bcore-split.py` copies these 4 files into a NewBlackbox
checkout (overwriting upstream) and adds CloneX's host package
(`/data/data/com.clonex.space`) to the hidden-paths list in
`Bcore/src/main/cpp/Utils/AntiDetection.cpp` (next to upstream's
`top.niunaijun.blackboxa` entry), then verifies both.
