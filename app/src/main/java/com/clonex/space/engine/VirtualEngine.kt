package com.clonex.space.engine

import com.clonex.space.data.CloneEntry

/**
 * Result of an engine operation.
 */
sealed interface EngineResult {
    /** The requested operation succeeded. */
    data class Ok(val message: String) : EngineResult

    /** The engine does not implement this operation. */
    data class NotImplemented(val reason: String) : EngineResult

    /** The operation was attempted and failed for the given reason. */
    data class Failed(val reason: String) : EngineResult
}

/**
 * The virtual-space engine contract.
 *
 * A real implementation must:
 *  - install an APK into an isolated virtual space (own data dir, own
 *    package state) without installing it on the phone's system,
 *  - launch it in a stub process with hooked system services so the app
 *    believes it is normally installed,
 *  - keep each clone (userId) isolated from the others and from the phone.
 *
 * v3.0: the NewBlackbox engine (BlackBoxCore) is the implementation.
 * Default implementations return NotImplemented so that
 * StubVirtualEngine still compiles unchanged.
 */
interface VirtualEngine {

    /** Install [apkPath] into the virtual space for virtual user [userId]. */
    suspend fun installPackageAsUser(apkPath: String, userId: Int): EngineResult

    /** Launch an already-installed virtual app. */
    suspend fun launchApp(packageName: String, userId: Int): EngineResult

    /** Remove a virtual app from the virtual space. */
    suspend fun uninstallPackage(packageName: String, userId: Int): EngineResult

    /** Clones currently known to the engine (may be empty in the stub). */
    suspend fun listClones(): List<CloneEntry>

    /**
     * v2.0.1: false when the engine failed to start (its init threw) —
     * callers must not delete library entries or trust an empty clone
     * list while the engine is down. Default true (stub / healthy).
     */
    suspend fun engineReady(): Boolean = true

    // --- v3.0: NewBlackbox (BlackBoxCore) operations ----------------------

    /** Clone an app already installed on the phone (installPackageAsUser). */
    suspend fun installFromHost(packageName: String, userId: Int): EngineResult =
        EngineResult.NotImplemented("installFromHost is not implemented by this engine.")

    /**
     * Install one APK file or a directory of split APKs
     * (a directory must contain only APKs of one app; the base APK is
     * installed first, then its splits are copied into the engine's
     * splits dir for that package).
     */
    suspend fun installApkFiles(apkPathOrDir: String, userId: Int): EngineResult =
        EngineResult.NotImplemented("installApkFiles is not implemented by this engine.")

    /** Wipe a clone's virtual data (clearPackage). */
    suspend fun clearCloneData(packageName: String, userId: Int): EngineResult =
        EngineResult.NotImplemented("clearCloneData is not implemented by this engine.")

    /** Wipe a clone's virtual cache (its data/cache dir). */
    suspend fun clearCloneCache(packageName: String, userId: Int): EngineResult =
        EngineResult.NotImplemented("clearCloneCache is not implemented by this engine.")

    /** Kill a clone's running processes (stopPackage). */
    suspend fun killClone(packageName: String, userId: Int): EngineResult =
        EngineResult.NotImplemented("killClone is not implemented by this engine.")

    /** First free virtual user space for [packageName]. */
    suspend fun availableUser(packageName: String): Int = 0

    /** True when Google Mobile Services are installed for [userId]. */
    suspend fun isGmsInstalled(userId: Int): Boolean = false

    /** Install Google Mobile Services into the virtual space (installGms). */
    suspend fun installGms(userId: Int): EngineResult =
        EngineResult.NotImplemented("installGms is not implemented by this engine.")

    /** Remove Google Mobile Services from the virtual space (uninstallGms). */
    suspend fun uninstallGms(userId: Int): EngineResult =
        EngineResult.NotImplemented("uninstallGms is not implemented by this engine.")
}
