# Add project specific ProGuard rules here.
# CloneX MVP: Room keeps its entities via @Entity annotations; the default
# Android rules already cover them. Keep this file for future rules
# (e.g. keep rules for the native virtual engine when it lands).
-keep class com.clonex.space.data.** { *; }

# v3.0: NewBlackbox engine — reflection-driven (hidden-API bypass, hook
# wiring, IPC callbacks invoked by reflection). Keep the whole engine
# namespace unobfuscated so the public API (BlackBoxCore, BEnvironment,
# InstallResult, ClientConfiguration) survives R8.
-keep class top.niunaijun.blackbox.** { *; }
-keepclassmembers class top.niunaijun.blackbox.** { *; }
# Bcore's own consumer-rules.pro also applies; repeat the key keeps here
# so they survive even if the consumer rules are ever dropped.
-keep class top.niunaijun.blackbox.BlackBoxCore { *; }
-keep class top.niunaijun.blackbox.entity.pm.InstallResult { *; }
-keep class top.niunaijun.blackbox.app.configuration.ClientConfiguration { *; }
-keep class top.niunaijun.blackbox.core.env.BEnvironment { *; }
