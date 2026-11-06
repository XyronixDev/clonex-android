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
 * v2.0: the MultiApp opensdk (HackApi) operations are part of this
 * contract. Default implementations return NotImplemented so that
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

    // --- v2.0: MultiApp opensdk (HackApi) operations -----------------------

    /** Clone an app already installed on the phone (installPackageFromHost). */
    suspend fun installFromHost(packageName: String, userId: Int): EngineResult =
        EngineResult.NotImplemented("installFromHost is not implemented by this engine.")

    /**
     * Install one APK file or a directory of split APKs
     * (installApkFiles — a directory must contain only APKs of one app).
     */
    suspend fun installApkFiles(apkPathOrDir: String, userId: Int): EngineResult =
        EngineResult.NotImplemented("installApkFiles is not implemented by this engine.")

    /** Wipe a clone's virtual data (deletePackageData). */
    suspend fun clearCloneData(packageName: String, userId: Int): EngineResult =
        EngineResult.NotImplemented("clearCloneData is not implemented by this engine.")

    /** Wipe a clone's virtual cache (deletePackageCache). */
    suspend fun clearCloneCache(packageName: String, userId: Int): EngineResult =
        EngineResult.NotImplemented("clearCloneCache is not implemented by this engine.")

    /** Kill a clone's running processes (killApplication). */
    suspend fun killClone(packageName: String, userId: Int): EngineResult =
        EngineResult.NotImplemented("killClone is not implemented by this engine.")

    /** First free virtual user space for [packageName] (getAvailableUser). */
    suspend fun availableUser(packageName: String): Int = 0

    /**
     * True when the engine says this clone needs the 32-bit assist package
     * (getPackageSetting "request_assistant").
     */
    suspend fun needsAssist(packageName: String, userId: Int): Boolean = false

    /** Engine runtime properties (getRuntimeProperties), empty when unavailable. */
    suspend fun runtimeProperties(): Map<String, Any?> = emptyMap()
}
