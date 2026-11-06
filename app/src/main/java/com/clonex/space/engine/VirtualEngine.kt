package com.clonex.space.engine

import com.clonex.space.data.CloneEntry

/**
 * Result of an engine operation. The MVP stub never pretends a virtual
 * launch worked — see StubVirtualEngine.
 */
sealed interface EngineResult {
    /** The requested bookkeeping happened (e.g. entry saved to the library). */
    data class Ok(val message: String) : EngineResult

    /** Real virtualization is not implemented yet — honest, not faked. */
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
}
