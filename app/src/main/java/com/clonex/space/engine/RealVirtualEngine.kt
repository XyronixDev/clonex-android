package com.clonex.space.engine

import android.content.Context
import com.clonex.space.CloneXApplication
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.clonex.space.data.CloneDao
import com.clonex.space.data.CloneEntry
import com.hack.opensdk.CmdConstants
import com.hack.opensdk.HackApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * CloneX v2.0 real virtual engine — MultiApp opensdk (HackApi).
 *
 * Verified against the opensdk sources (WaxMoon/MultiApp, pinned commit
 * 3de55af64ec925de390cbf30bb82f279a51945e0, opensdk submodule eaab164):
 *
 *  - HackApi.installPackageFromHost(String, int, boolean): int
 *  - HackApi.installApkFiles(String, int, boolean): int
 *  - HackApi.uninstallPackage(String, int): int
 *  - HackApi.deletePackageData(String, int): int
 *  - HackApi.deletePackageCache(String, int): int
 *  - HackApi.startActivity(Intent, int): int
 *  - HackApi.startPackage(String, int): boolean (deprecated, fallback only)
 *  - HackApi.getLaunchIntentForPackage(String, int): Intent?
 *  - HackApi.killApplication(int, String, String): void
 *  - HackApi.getInstalledPackages(int, int): List<String>
 *  - HackApi.getAvailableUserSpace(): int[]
 *  - HackApi.getAvailableUser(String): int
 *  - HackApi.getPackageSetting(String, int, int): Bundle
 *  - HackApi.getRuntimeProperties(): Map<String, Object>
 *
 * Return codes: INSTALL_SUCCEEDED = 1 is documented in HackApi's javadoc;
 * INSTALL_FAILED_ALREADY_EXISTS = -1, INSTALL_FAILED_INVALID_APK = -2 and
 * INSTALL_FAILED_INVALID_URI = -3 are the MultiApp demo app's own constants
 * (AppListActivity.kt) — the engine jar's own codes were not visible from
 * the sources, so anything other than 1 is reported with its raw code.
 * START_SUCCESS = 0; startActivity also returns 1/2/3 for
 * "returned to caller / task to front / delivered to top", which are
 * success variants, so any return >= 0 counts as launched.
 *
 * userId = the clone's userSpace = cloneIndex (0, 1, 2, ...). Each clone of
 * the same package runs under its own userId inside the virtual space.
 *
 * All HackApi calls are binder IPC into the engine and can block, so every
 * method runs on Dispatchers.IO and never throws out of the engine — a
 * failure is always an EngineResult.Failed.
 */
class RealVirtualEngine(
    private val context: Context,
    private val dao: CloneDao
) : VirtualEngine {

    companion object {
        private const val TAG = "RealVirtualEngine"

        const val INSTALL_SUCCEEDED = 1
        const val INSTALL_FAILED_ALREADY_EXISTS = -1
        const val INSTALL_FAILED_INVALID_APK = -2
        const val INSTALL_FAILED_INVALID_URI = -3
        const val DELETE_SUCCEEDED = 1
        const val START_SUCCESS = 0

        /**
         * Packages that belong to the engine itself — never shown as
         * clone cards (v2.0.1: the bundled Play Store must not appear
         * as a clone the user never added).
         */
        val INTERNAL_PACKAGES = setOf(
            "com.waxmoon.ma.gp",        // bundled Play Store (Google Suite)
            "com.clonex.space.assist", // 32-bit assist package
            "com.clonex.space",         // CloneX itself
            "com.google.android.gms",   // Google Services (Google Suite support)
            "com.google.android.gsf",   // Google Services Framework
            "com.android.vending"       // Play Store (host copy)
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
                    ?: return@withContext EngineResult.Failed(
                        "Could not read this APK file."
                    )
                val (packageName, label) = parsed
                val ret = HackApi.installApkFiles(apkPath, userId, false)
                val result = installResult(ret, label, userId)
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
                val ret = HackApi.installPackageFromHost(packageName, userId, false)
                val result = installResult(ret, label, userId)
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
                // For a split-APK directory every APK shares one package
                // name — parse the first readable APK for the library entry.
                val parsed = when {
                    file.isFile -> parseApk(apkPathOrDir)
                    file.isDirectory -> file.listFiles()
                        ?.filter { it.name.endsWith(".apk", ignoreCase = true) }
                        ?.sortedBy { it.name }
                        ?.firstNotNullOfOrNull { parseApk(it.absolutePath) }
                    else -> null
                }
                val ret = HackApi.installApkFiles(apkPathOrDir, userId, false)
                if (parsed == null) {
                    return@withContext if (ret == INSTALL_SUCCEEDED) {
                        EngineResult.Ok(
                            "Installed in the virtual space (Clone ${userId + 1}). " +
                                "The library entry will appear after the next sync."
                        )
                    } else {
                        installResult(ret, "App", userId)
                    }
                }
                val (packageName, label) = parsed
                val result = installResult(ret, label, userId)
                if (result is EngineResult.Ok) {
                    insertEntry(packageName, label, userId, null)
                }
                result
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
                // v2.0.2 — launch exactly like the MultiApp demo
                // (AppListActivity.startApp). v2.0.0/v2.0.1 used the
                // engine's getLaunchIntentForPackage FIRST and treated any
                // startActivity return >= 0 as success — on the user's
                // phone that reported "Launching Instagram…" while the
                // clone never opened (return 1 = returned to caller,
                // i.e. NOT started). The demo instead:
                //  1. takes the HOST PackageManager's launch intent and
                //     adds FLAG_ACTIVITY_RESET_TASK_IF_NEEDED;
                //  2. falls back to the engine's getLaunchIntentForPackage
                //     only for clones the host never had (APK imports);
                //  3. treats ONLY 0 (START_SUCCESS) as launched — any
                //     other code is reported, never shown as "Launching…".
                val hostIntent = try {
                    context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        )
                    }
                } catch (e: Exception) {
                    null
                }
                val intent = hostIntent
                    ?: HackApi.getLaunchIntentForPackage(packageName, userId)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    }
                if (intent != null) {
                    val ret = HackApi.startActivity(intent, userId)
                    return@withContext if (ret == START_SUCCESS) {
                        EngineResult.Ok("Launching ${hostLabel(packageName)}…")
                    } else {
                        EngineResult.Failed("Launch failed (engine code $ret).")
                    }
                }
                @Suppress("DEPRECATION")
                val started = HackApi.startPackage(packageName, userId)
                if (started) {
                    EngineResult.Ok("Launching ${hostLabel(packageName)}…")
                } else {
                    EngineResult.Failed(
                        "No launchable activity found for $packageName in this space."
                    )
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
                val ret = HackApi.uninstallPackage(packageName, userId)
                if (ret == DELETE_SUCCEEDED) {
                    EngineResult.Ok("Removed from the virtual space.")
                } else {
                    EngineResult.Failed("Uninstall failed (engine code $ret).")
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
                val ret = HackApi.deletePackageData(packageName, userId)
                if (ret == DELETE_SUCCEEDED) {
                    EngineResult.Ok("Clone data cleared.")
                } else {
                    EngineResult.Failed("Clear data failed (engine code $ret).")
                }
            } catch (e: Exception) {
                Log.w(TAG, "clearCloneData failed", e)
                EngineResult.Failed("Clear data failed: ${e.message}")
            }
        }

    override suspend fun clearCloneCache(packageName: String, userId: Int): EngineResult =
        withContext(Dispatchers.IO) {
            try {
                engineDown()?.let { return@withContext EngineResult.Failed("Virtual engine failed to start: $it") }
                val ret = HackApi.deletePackageCache(packageName, userId)
                if (ret == DELETE_SUCCEEDED) {
                    EngineResult.Ok("Clone cache cleared.")
                } else {
                    EngineResult.Failed("Clear cache failed (engine code $ret).")
                }
            } catch (e: Exception) {
                Log.w(TAG, "clearCloneCache failed", e)
                EngineResult.Failed("Clear cache failed: ${e.message}")
            }
        }

    override suspend fun killClone(packageName: String, userId: Int): EngineResult =
        withContext(Dispatchers.IO) {
            try {
                engineDown()?.let { return@withContext EngineResult.Failed("Virtual engine failed to start: $it") }
                HackApi.killApplication(userId, packageName, "CloneX user request")
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
                HackApi.getAvailableUser(packageName)
            } catch (e: Exception) {
                Log.w(TAG, "getAvailableUser failed, using 0", e)
                0
            }
        }

    override suspend fun needsAssist(packageName: String, userId: Int): Boolean =
        withContext(Dispatchers.IO) {
            try {
                engineDown()?.let { return@withContext false }
                val settings = HackApi.getPackageSetting(packageName, userId, 0)
                settings?.getBoolean(CmdConstants.PKG_SET_REQUEST_ASSISTANT, false) ?: false
            } catch (e: Exception) {
                Log.w(TAG, "getPackageSetting failed", e)
                false
            }
        }

    override suspend fun runtimeProperties(): Map<String, Any?> =
        withContext(Dispatchers.IO) {
            try {
                engineDown()?.let { return@withContext emptyMap() }
                HackApi.getRuntimeProperties()?.mapValues { it.value } ?: emptyMap()
            } catch (e: Exception) {
                Log.w(TAG, "getRuntimeProperties failed", e)
                emptyMap()
            }
        }

    override suspend fun listClones(): List<CloneEntry> =
        withContext(Dispatchers.IO) {
            try {
                engineDown()?.let { return@withContext emptyList() }
                val spaces = try {
                    HackApi.getAvailableUserSpace()?.toList() ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
                // getAvailableUserSpace lists the spaces the engine knows;
                // scan 0..9 as well so a fresh engine still reports clones.
                val userIds = (spaces + (0..9)).distinct()
                val entries = mutableListOf<CloneEntry>()
                for (userId in userIds) {
                    val pkgs = try {
                        HackApi.getInstalledPackages(0, userId)
                    } catch (e: Exception) {
                        null
                    } ?: continue
                    for (pkg in pkgs) {
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

    // --- helpers ------------------------------------------------------------

    private fun installResult(ret: Int, label: String, userId: Int): EngineResult =
        when (ret) {
            INSTALL_SUCCEEDED -> EngineResult.Ok(
                "$label installed as Clone ${userId + 1}."
            )
            INSTALL_FAILED_ALREADY_EXISTS -> EngineResult.Failed(
                "$label is already installed in this space."
            )
            INSTALL_FAILED_INVALID_APK -> EngineResult.Failed(
                "Invalid APK — the file could not be installed."
            )
            INSTALL_FAILED_INVALID_URI -> EngineResult.Failed(
                "Invalid install path."
            )
            else -> EngineResult.Failed("Install failed (engine code $ret).")
        }

    private suspend fun insertEntry(
        packageName: String,
        label: String,
        userId: Int,
        apkPath: String?
    ) {
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
