package com.clonex.space

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.app.configuration.ClientConfiguration
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CloneX v3.0 application — the NewBlackbox virtual engine
 * (BlackBoxCore) is initialized here:
 *
 *  - attachBaseContext: BlackBoxCore.get().doAttachBaseContext(base,
 *    ClientConfiguration) — hooks the process into the virtual space.
 *    Must run in EVERY process of this APK (main, :black server, and
 *    the :pN stub processes that host cloned apps).
 *  - onCreate: BlackBoxCore.get().doCreate() — brings up the engine
 *    server (the :black process, DaemonService keep-alive).
 *
 * v3.0 replaces the MultiApp opensdk engine (v2.0–v2.0.10): the opensdk
 * server rejected every clone launch on the user's phone (engine code
 * 102) and sometimes reported success while the clone's :agent process
 * died at once. NewBlackbox is actively maintained (Android 13–16),
 * its launch is a single boolean (no int codes), and its hidden-API
 * bypass is 3-layer (generated accessors + FreeReflection + native)
 * instead of the opensdk's bare reflection.
 *
 * Hardening (kept from v2.0.1):
 *  - If the engine init throws, the app must NOT die at startup. The
 *    failure is recorded in [engineInitError] and the app continues in
 *    degraded mode (RealVirtualEngine refuses every op with a clear
 *    message instead of calling into a dead engine).
 *  - A global uncaught-exception handler writes the last crash to
 *    filesDir/crash-last.txt so a crash is diagnosable without logcat
 *    (Device Info shows it).
 */
class CloneXApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        try {
            BlackBoxCore.get().doAttachBaseContext(
                base,
                object : ClientConfiguration() {
                    override fun getHostPackageName(): String = base.packageName
                }
            )
        } catch (e: Throwable) {
            engineInitError = "attachBaseContext: ${e.message}"
            Log.e(TAG, "engine init failed in attachBaseContext", e)
        }
        instance = this
    }

    override fun onCreate() {
        super.onCreate()
        try {
            BlackBoxCore.get().doCreate()
        } catch (e: Throwable) {
            engineInitError = "onCreate: ${e.message}"
            Log.e(TAG, "engine init failed in onCreate", e)
        }
        if (engineInitError == null) {
            engineInitOk = true
        }
        installCrashHandler()
    }

    /** Record the last uncaught crash (stack trace + device) for Device Info. */
    private fun installCrashHandler() {
        try {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    val stamp = SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss", Locale.US
                    ).format(Date())
                    val text = StringBuilder()
                        .append("CloneX crash at $stamp\n")
                        .append("Android ${Build.VERSION.RELEASE} ")
                        .append("(SDK ${Build.VERSION.SDK_INT}) ")
                        .append("${Build.MANUFACTURER} ${Build.MODEL}\n")
                        .append("CloneX 3.0.0\n")
                        .append(Log.getStackTraceString(throwable))
                        .toString()
                    File(filesDir, CRASH_FILE).writeText(text)
                } catch (ignored: Exception) {
                    // Never let the handler itself crash the crash path.
                }
                previous?.uncaughtException(thread, throwable)
            }
        } catch (e: Exception) {
            Log.w(TAG, "installCrashHandler failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "CloneXApplication"

        private const val CRASH_FILE = "crash-last.txt"

        @Volatile
        var instance: CloneXApplication? = null
            private set

        /** Non-null when the engine init threw; the app runs degraded. */
        @Volatile
        var engineInitError: String? = null
            private set

        @Volatile
        var engineInitOk: Boolean = false
            private set

        fun engineStatus(): String =
            if (engineInitError == null) "OK" else "failed to start: $engineInitError"

        /** Last recorded crash, or null when none was recorded. */
        fun readLastCrash(context: Context): String? {
            return try {
                val file = File(context.filesDir, CRASH_FILE)
                if (file.exists()) file.readText().trim().takeIf { it.isNotEmpty() } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}
