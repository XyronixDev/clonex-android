package com.clonex.space.data

import androidx.room.Entity

/**
 * One cloned app entry in the CloneX library.
 *
 * A row means "the user asked CloneX to keep a separate copy of this app".
 * It does NOT by itself mean the app is running inside a virtual space —
 * real virtual install/launch is the engine's job, see
 * com.clonex.space.engine.ENGINE_NOTES.md.
 *
 * cloneIndex lets the same package appear more than once (Clone 1, Clone 2…)
 * — each clone gets its own userId inside the virtual space.
 */
@Entity(
    tableName = "clones",
    primaryKeys = ["packageName", "cloneIndex"]
)
data class CloneEntry(
    val packageName: String,
    val label: String,
    val cloneIndex: Int = 0,
    /** Virtual-space user id this clone runs under. 0 = first clone. */
    val userId: Int = 0,
    /** Path of the APK file copied into CloneX storage, if imported via SAF. */
    val apkPath: String? = null,
    val installedAt: Long = System.currentTimeMillis()
)
