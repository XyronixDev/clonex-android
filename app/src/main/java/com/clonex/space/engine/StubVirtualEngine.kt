package com.clonex.space.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.clonex.space.data.CloneDao
import com.clonex.space.data.CloneEntry

/**
 * MVP stub of the virtual engine.
 *
 * What it REALLY does:
 *  - reads the real package name / label out of an APK file with
 *    PackageManager and saves the clone entry into the Room library
 *    (so Add App / Import APK visibly work and survive restarts),
 *  - reports the state honestly.
 *
 * What it does NOT do (and never claims to):
 *  - install an APK into a virtual space,
 *  - launch an app inside a virtual space,
 *  - isolate data between clones.
 *
 * launchApp() / uninstallPackage() always return NotImplemented until a
 * real engine (maintained VirtualApp fork + native libs) is integrated.
 * See ENGINE_NOTES.md next to this file.
 */
class StubVirtualEngine(
    private val context: Context,
    private val dao: CloneDao
) : VirtualEngine {

    override suspend fun installPackageAsUser(apkPath: String, userId: Int): EngineResult {
        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val pkgInfo = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageArchiveInfo(
                    apkPath,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                pm.getPackageArchiveInfo(apkPath, 0)
            } ?: return EngineResult.Failed("Could not read this APK file.")
            val packageName = pkgInfo.packageName
            val appInfo = pkgInfo.applicationInfo
                ?: return EngineResult.Failed("Could not read this APK file.")
            val label = appInfo.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                ?: packageName
            val index = dao.countForPackage(packageName)
            dao.insert(
                CloneEntry(
                    packageName = packageName,
                    label = label,
                    cloneIndex = index,
                    userId = userId,
                    apkPath = apkPath
                )
            )
            EngineResult.Ok(
                "$label saved to the CloneX library as Clone ${index + 1}. " +
                    "Real virtual install needs the virtual engine — " +
                    "see ENGINE_NOTES.md."
            )
        } catch (e: Exception) {
            EngineResult.Failed("Could not save clone entry: ${e.message}")
        }
    }

    override suspend fun launchApp(packageName: String, userId: Int): EngineResult {
        return EngineResult.NotImplemented(
            "Virtual launch is NOT implemented in this MVP. The app is in " +
                "your CloneX library, but running it inside a virtual space " +
                "needs the real virtual engine (native libs), which is not " +
                "bundled yet. See ENGINE_NOTES.md."
        )
    }

    override suspend fun uninstallPackage(packageName: String, userId: Int): EngineResult {
        return EngineResult.NotImplemented(
            "Virtual uninstall is NOT implemented in this MVP — nothing was " +
                "installed into a virtual space, so there is nothing to remove. " +
                "Use long-press > Remove in the library to delete the entry."
        )
    }

    override suspend fun listClones(): List<CloneEntry> = emptyList()
}
