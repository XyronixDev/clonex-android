package com.clonex.space.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.clonex.space.CloneXApplication
import com.clonex.space.data.CloneDao
import com.clonex.space.data.CloneEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.entity.pm.InstallResult
import java.io.File

/**
 * CloneX v3.0 real virtual engine — NewBlackbox (BlackBoxCore).
 *
 * Verified against the NewBlackbox sources (ALEX5402/NewBlackbox, pinned
 * commit 89b59836c66f173756a4ae258cf379a957649820, HEAD 2026-07-19):
 *
 *  - BlackBoxCore.get().installPackageAsUser(String, int): InstallResult
 *      (clone a host-installed app by package name)
 *  - BlackBoxCore.get().installPackageAsUser(File, int): InstallResult
 *      (install one APK file)
 *  - BlackBoxCore.get().launchApk(String, int): boolean
 *      (true = launched; no int codes — the opensdk 102 is gone)
 *  - BlackBoxCore.get().uninstallPackageAsUser(String, int): void
 *  - BlackBoxCore.get().isInstalled(String, int): boolean
 *  - BlackBoxCore.get().getInstalledApplications(int, int): List<ApplicationInfo>
 *  - BlackBoxCore.get().clearPackage(String, int): void  (wipe data)
 *  - BlackBoxCore.get().stopPackage(String, int): void   (kill)
 *  - BlackBoxCore.get().getUsers(): List<BUserInfo>
 *  - BlackBoxCore.get().isInstallGms(int): boolean
 *  - BlackBoxCore.get().installGms(int): InstallResult
 *  - BlackBoxCore.get().uninstallGms(int): boolean
 *  - BlackBoxCore.get().areServicesAvailable(): boolean
 *  - InstallResult: public boolean success, String packageName, String msg
 *
 * Split APKs (Instagram, Facebook, WhatsApp are App Bundles): upstream
 * NewBlackbox clones only base.apk. The MaxCloner split patch is applied
 * to the Bcore source in CI (tools/patch-bcore-split.py) — the engine
 * then copies every split and extracts native libs from each split when
 * installing a host-installed app, so installFromHost just works.
 * installApkFiles (a directory of APKs) installs the base file, then
 * copies the remaining splits into the engine's splits dir for that
 * package (see [splitApkDir]).
 *
 * userId = the clone's userSpace = cloneIndex (0, 1, 2, ...). Each clone of
 * the same package runs under its own userId inside the virtual space.
 * NewBlackbox auto-creates a user on install, so no explicit createUser
 * call is needed.
 *
 * All BlackBoxCore calls are binder IPC into the engine and can block, so
 * every method runs on Dispatchers.IO and never throws out of the engine
 * — a failure is always an EngineResult.Failed.
 */
class RealVirtualEngine(
    private val context: Context,
    private val dao: CloneDao
) : VirtualEngine {

    companion object {
        private const val TAG = "RealVirtualEngine"

        /**
         * Packages that belong to the engine itself — never shown as
         * clone cards. Google Services are managed by installGms, not
         * by the user, so they must not appear as clones either.
         */
        val INTERNAL_PACKAGES = setOf(
            "com.clonex.space",         // CloneX itself
            "com.google.android.gms",   // Google Services (Google Suite)
            "com.google.android.gsf",   // Google Services Framework
            "com.android.vending"       // Play Store (GMS registry)
        )
    }

    /** Non-null reason when the engine failed to start (degraded mode). */
    private fun engineDown(): String? = CloneXApplication.engineInitError

    /** v2.0.1: the startup sync must not trust an empty list while down. */
    override suspend fun engineReady(): Boolean =
        CloneXApplication.engineInitError == null

    // --- install ------------------------------------------------------------

    override suspend fun installPackageAsUser(apkPath: String, userId: Int): EngineResult =
        withContext(Dispatchers.IO) {
            try {
                engineDown()?.let { return@withContext EngineResult.Failed("Virtual engine failed to start: $it") }
                val parsed = parseApk(apkPath)
                    ?: return@withContext EngineResult.Failed("Could not read this APK file.")
                val (packageName, label) = parsed
                val result = installResult(
                    BlackBoxCore.get().installPackageAsUser(File(apkPath), userId),
                    label, userId
                )
                if (result is EngineResult.Ok) {
                    insertEntry(packageName, label, userId, apkPath)
                }
                result
            } catch (e: Exception) {
                Log.w(TAG, "installPackageAsUser failed", e)
                EngineResult.Failed("Install failed: ${e.message}")
            }
        }

    override suspend fun installFromHost(packageName: String, userId: Int): EngineResult =
        withContext(Dispatchers.IO) {
            try {
                engineDown()?.let { return@withContext EngineResult.Failed("Virtual engine failed to start: $it") }
                val label = hostLabel(packageName)
                // The MaxCloner split patch (applied to Bcore in CI) makes
                // the engine copy base + all splits and extract native
                // libs from each split, so a host-installed App Bundle
                // installs whole with this one call.
                val result = installResult(
                    BlackBoxCore.get().installPackageAsUser(packageName, userId),
                    label, userId
                )
                if (result is EngineResult.Ok) {
                    insertEntry(packageName, label, userId, null)
                }
                result
            } catch (e: Exception) {
                Log.w(TAG, "installFromHost failed", e)
                EngineResult.Failed("Install failed: ${e.message}")
            }
        }

    override suspend fun installApkFiles(apkPathOrDir: String, userId: Int): EngineResult =
        withContext(Dispatchers.IO) {
            try {
                engineDown()?.let { return@withContext EngineResult.Failed("Virtual engine failed to start: $it") }
                val file = File(apkPathOrDir)
                val parsed = when {
                    file.isFile -> parseApk(apkPathOrDir)
                    file.isDirectory -> file.listFiles()
                        ?.filter { it.name.endsWith(".apk", ignoreCase = true) }
                        ?.sortedBy { it.name }
                        ?.firstNotNullOfOrNull { parseApk(it.absolutePath) }
                    else -> null
                } ?: return@withContext EngineResult.Failed("Could not read this APK file.")
                val (packageName, label) = parsed
                // Install the base APK first (single file), then copy any
                // sibling splits into the engine's splits dir for this
                // package — the engine re-derives split paths by listing
                // that dir, so they are picked up at launch.
                val baseApk = when {
                    file.isFile -> file
                    else -> file.listFiles()
                        ?.filter { it.name.endsWith(".apk", ignoreCase = true) }
                        ?.sortedBy { it.name }
                        ?.firstOrNull()
                        ?: return@withContext EngineResult.Failed("No APK files found in the picked folder.")
                }
                val baseResult = installResult(
                    BlackBoxCore.get().installPackageAsUser(baseApk, userId),
                    label, userId
                )
                if (baseResult !is EngineResult.Ok) return@withContext baseResult
                if (file.isDirectory) {
                    val splitDir = splitApkDir(packageName).apply { mkdirs() }
                    file.listFiles()
                        ?.filter { it.name.endsWith(".apk", ignoreCase = true) && it != baseApk }
                        ?.forEach { split ->
                            try {
                                split.copyTo(File(splitDir, split.name), overwrite = true)
                            } catch (e: Exception) {
                                Log.w(TAG, "copying split ${split.name} failed", e)
                            }
                        }
                }
                insertEntry(packageName, label, userId, null)
                baseResult
            } catch (e: Exception) {
                Log.w(TAG, "installApkFiles failed", e)
                EngineResult.Failed("Install failed: ${e.message}")
            }
        }

    // --- launch / uninstall -------------------------------------------------

    override suspend fun launchApp(packageName: String, userId: Int): EngineResult =
        withContext(Dispatchers.IO) {
            try {
                engineDown()?.let { return@withContext EngineResult.Failed("Virtual engine failed to start: $it") }
                // One call, one boolean — no int codes, no 3-shot
                // sequence. The opensdk's undocumented 102 cannot occur
                // here; a false means "no launchable activity" or the
                // engine refused the launch.
                if (BlackBoxCore.get().launchApk(packageName, userId)) {
                    EngineResult.Ok("Launching ${hostLabel(packageName)}…")
                } else {
                    EngineResult.Failed("Clone could not start.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "launchApp failed", e)
                EngineResult.Failed("Launch failed: ${e.message}")
            }
        }

    override suspend fun uninstallPackage(packageName: String, userId: Int): EngineResult =
        withContext(Dispatchers.IO) {
            try {
                engineDown()?.let { return@withContext EngineResult.Failed("Virtual engine failed to start: $it") }
                BlackBoxCore.get().uninstallPackageAsUser(packageName, userId)
                if (!BlackBoxCore.get().isInstalled(packageName, userId)) {
                    EngineResult.Ok("Removed from the virtual space.")
                } else {
                    EngineResult.Failed("Uninstall failed.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "uninstallPackage failed", e)
                EngineResult.Failed("Uninstall failed: ${e.message}")
            }
        }

    // --- clone maintenance --------------------------------------------------

    override suspend fun clearCloneData(packageName: String, userId: Int): EngineResult =
        withContext(Dispatchers.IO) {
            try {
                engineDown()?.let { return@withContext EngineResult.Failed("Virtual engine failed to start: $it") }
                BlackBoxCore.get().clearPackage(packageName, userId)
                EngineResult.Ok("Clone data cleared.")
            } catch (e: Exception) {
                Log.w(TAG, "clearCloneData failed", e)
                EngineResult.Failed("Clear data failed: ${e.message}")
            }
        }

    override suspend fun clearCloneCache(packageName: String, userId: Int): EngineResult =
        withContext(Dispatchers.IO) {
            try {
                engineDown()?.let { return@withContext EngineResult.Failed("Virtual engine failed to start: $it") }
                // NewBlackbox has no cache-only API; delete the clone's
                // data/cache dir contents directly (same path the engine
                // uses: virtualRoot/data/user/<userId>/<pkg>/cache).
                val cacheDir = dataCacheDir(packageName, userId)
                cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                EngineResult.Ok("Clone cache cleared.")
            } catch (e: Exception) {
                Log.w(TAG, "clearCloneCache failed", e)
                EngineResult.Failed("Clear cache failed: ${e.message}")
            }
        }

    override suspend fun killClone(packageName: String, userId: Int): EngineResult =
        withContext(Dispatchers.IO) {
            try {
                engineDown()?.let { return@withContext EngineResult.Failed("Virtual engine failed to start: $it") }
                BlackBoxCore.get().stopPackage(packageName, userId)
                EngineResult.Ok("Clone stopped.")
            } catch (e: Exception) {
                Log.w(TAG, "killClone failed", e)
                EngineResult.Failed("Stop failed: ${e.message}")
            }
        }

    // --- engine queries -----------------------------------------------------

    override suspend fun availableUser(packageName: String): Int =
        withContext(Dispatchers.IO) {
            try {
                engineDown()?.let { return@withContext 0 }
                // First userId (0..9) where this package is not installed.
                // NewBlackbox auto-creates the user on install.
                for (userId in 0..9) {
                    if (!BlackBoxCore.get().isInstalled(packageName, userId)) {
                        return@withContext userId
                    }
                }
                10
            } catch (e: Exception) {
                Log.w(TAG, "availableUser failed, using 0", e)
                0
            }
        }

    override suspend fun listClones(): List<CloneEntry> =
        withContext(Dispatchers.IO) {
            try {
                engineDown()?.let { return@withContext emptyList() }
                val entries = mutableListOf<CloneEntry>()
                for (userId in 0..9) {
                    val apps = try {
                        BlackBoxCore.get().getInstalledApplications(0, userId)
                    } catch (e: Exception) {
                        null
                    } ?: continue
                    for (app in apps) {
                        val pkg = app.packageName
                        if (pkg in INTERNAL_PACKAGES) continue
                        entries += CloneEntry(
                            packageName = pkg,
                            label = hostLabel(pkg),
                            cloneIndex = userId,
                            userId = userId
                        )
                    }
                }
                entries
            } catch (e: Exception) {
                Log.w(TAG, "listClones failed", e)
                emptyList()
            }
        }

    // --- Google Mobile Services ----------------------------------------------

    override suspend fun isGmsInstalled(userId: Int): Boolean =
        withContext(Dispatchers.IO) {
            try {
                engineDown()?.let { return@withContext false }
                BlackBoxCore.get().isInstallGms(userId)
            } catch (e: Exception) {
                Log.w(TAG, "isGmsInstalled failed", e)
                false
            }
        }

    override suspend fun installGms(userId: Int): EngineResult =
        withContext(Dispatchers.IO) {
            try {
                engineDown()?.let { return@withContext EngineResult.Failed("Virtual engine failed to start: $it") }
                val result = BlackBoxCore.get().installGms(userId)
                if (result.success) {
                    EngineResult.Ok("Google services installed.")
                } else {
                    EngineResult.Failed(result.msg ?: "Google services install failed.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "installGms failed", e)
                EngineResult.Failed("Google services install failed: ${e.message}")
            }
        }

    override suspend fun uninstallGms(userId: Int): EngineResult =
        withContext(Dispatchers.IO) {
            try {
                engineDown()?.let { return@withContext EngineResult.Failed("Virtual engine failed to start: $it") }
                if (BlackBoxCore.get().uninstallGms(userId)) {
                    EngineResult.Ok("Google services removed.")
                } else {
                    EngineResult.Failed("Could not remove Google services.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "uninstallGms failed", e)
                EngineResult.Failed("Google services remove failed: ${e.message}")
            }
        }

    // --- helpers ------------------------------------------------------------

    private fun installResult(result: InstallResult, label: String, userId: Int): EngineResult =
        if (result.success) {
            EngineResult.Ok("$label installed as Clone ${userId + 1}.")
        } else {
            EngineResult.Failed(result.msg ?: "Install failed.")
        }

    private suspend fun insertEntry(
        packageName: String,
        label: String,
        userId: Int,
        apkPath: String?
    ) {
        // Engine support packages are NEVER library entries.
        if (packageName in INTERNAL_PACKAGES) return
        dao.insert(
            CloneEntry(
                packageName = packageName,
                label = label,
                cloneIndex = userId,
                userId = userId,
                apkPath = apkPath
            )
        )
    }

    /**
     * The engine's splits dir for a package, computed from the same
     * formula BEnvironment uses (virtualRoot = host data dir +
     * "/blackbox"; splits live at virtualRoot/data/app/<pkg>/splits).
     * Computed here instead of calling BEnvironment directly so the
     * build does not depend on a Bcore internal class surviving the
     * AAR's minification.
     */
    private fun splitApkDir(packageName: String): File {
        val dataDir = context.cacheDir.parentFile ?: context.filesDir
        return File(dataDir, "blackbox/data/app/$packageName/splits")
    }

    /** The clone's data/cache dir (same formula as BEnvironment). */
    private fun dataCacheDir(packageName: String, userId: Int): File {
        val dataDir = context.cacheDir.parentFile ?: context.filesDir
        return File(dataDir, "blackbox/data/user/$userId/$packageName/cache")
    }

    /** Label of an app installed on the phone (host), or the package name. */
    fun hostLabel(packageName: String): String {
        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val info = if (Build.VERSION.SDK_INT >= 33) {
                pm.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                pm.getApplicationInfo(packageName, 0)
            }
            info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: packageName
        } catch (e: Exception) {
            packageName
        }
    }

    /** (packageName, label) parsed out of an APK file, or null. */
    private fun parseApk(apkPath: String): Pair<String, String>? {
        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val info = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageArchiveInfo(
                    apkPath,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                pm.getPackageArchiveInfo(apkPath, 0)
            } ?: return null
            val appInfo = info.applicationInfo ?: return null
            val label = appInfo.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                ?: info.packageName
            info.packageName to label
        } catch (e: Exception) {
            null
        }
    }
}
