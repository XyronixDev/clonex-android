# Add project specific ProGuard rules here.
# CloneX MVP: Room keeps its entities via @Entity annotations; the default
# Android rules already cover them. Keep this file for future rules
# (e.g. keep rules for the native virtual engine when it lands).
-keep class com.clonex.space.data.** { *; }

# v2.0: MultiApp opensdk engine — heavily reflection-driven (Cmd exec,
# moon.jar DexClassLoader, IPackageObserver callbacks invoked by
# reflection). Keep the whole engine namespace unobfuscated, and keep its
# BuildConfig (MASTER_PACKAGE / ASSIST_PACKAGE / ENGINE_JAR_NAME) intact.
-keep class com.hack.** { *; }
-keep class com.hack.opensdk.BuildConfig { *; }
# The AAR's own consumer rules keep the IPackageObserver callbacks; repeat
# them here so they survive even if the consumer rules are ever dropped.
-keepclassmembers class ** implements com.hack.opensdk.IPackageObserver$Install {
    public void onPackageInstalled(...);
}
-keepclassmembers class ** implements com.hack.opensdk.IPackageObserver$Delete {
    public void onPackageDeleted(...);
}
