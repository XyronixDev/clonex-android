package com.clonex.space

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.hack.opensdk.HackApi
import com.hack.opensdk.HackApplication
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CloneX v2.0.1 application — the MultiApp opensdk (HackApi) virtual engine
 * is initialized by the parent class HackApplication:
 *
 *  - attachBaseContext: HackRuntime.install() extracts moon.jar from the
 *    AAR assets into filesDir/.plugin and loads the engine with a
 *    DexClassLoader; then CMD_APPLICATION_ATTACHBASE runs.
 *  - onCreate: CMD_APPLICATION_ONCREATE runs.
 *
 * The opensdk AAR MUST be built with MASTER_PACKAGE=com.clonex.space and
 * ASSIST_PACKAGE=com.clonex.space.assist (see .github/workflows/build.yml)
 * or HackApplication will look for the wrong master package.
 *
 * The engine runs in EVERY process of this APK — including the :core
 * service process and the :agent.pN stub processes the engine uses to
 * host cloned apps — so this init must stay light and must not throw.
 *
 * v2.0.1 hardening (phone test 2026-11-06: "CloneX keeps stopping"):
 *  - If the engine init throws, the app must NOT die at startup. The
 *    failure is recorded in [engineInitError] and the app continues in
 *    degraded mode (RealVirtualEngine refuses every op with a clear
 *    message instead of calling into a dead engine).
 *  - A global uncaught-exception handler writes the last crash to
 *    filesDir/crash-last.txt so a crash is diagnosable without logcat
 *    (Device Info shows it).
 *  - The engine's own foreground service (ForgroundService, started by
 *    HackServerProvider) posts a persistent notification on channel
 *    "hack_forground_id" at IMPORTANCE_DEFAULT — the blinking status-bar
 *    icon users complained about. The service must keep running (it
 *    keeps the engine server process alive), but the channel is
 *    recreated here at IMPORTANCE_LOW so it posts quietly: no icon,
 *    no sound. The channel id is unchanged, so the engine keeps working.
 */
class CloneXApplication : HackApplication() {

    override fun attachBaseContext(base: Context) {
        try {
            super.attachBaseContext(base)
        } catch (e: Throwable) {
            engineInitError = "attachBaseContext: ${e.message}"
            Log.e(TAG, "engine init failed in attachBaseContext", e)
        }
        instance = this
    }

    override fun onCreate() {
        try {
            super.onCreate()
        } catch (e: Throwable) {
            engineInitError = "onCreate: ${e.message}"
            Log.e(TAG, "engine init failed in onCreate", e)
        }
        if (engineInitError == null) {
            engineInitOk = true
        }
        installCrashHandler()
        quietEngineNotificationChannel()
        // Log-only lifecycle callback (mirrors the MultiApp demo's
        // MoonApplication.monitorApplicationEvent, using android.util.Log
        // instead of the engine's internal Slog).
        try {
            HackApi.registerApplicationCallback(object : HackApi.ApplicationCallback {
                override fun onInitAppContext(loadedApk: Any, appContext: Context) {
                    Log.d(TAG, "engine onInitAppContext: $appContext")
                }

                override fun onAttachBaseContext(app: Application) {
                    Log.d(TAG, "engine onAttachBaseContext: $app")
                }

                override fun onInstallProviders(app: Application) {
                    Log.d(TAG, "engine onInstallProviders: $app")
                }

                override fun onCreate(app: Application) {
                    Log.d(TAG, "engine onCreate: $app")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "registerApplicationCallback failed: ${e.message}")
        }
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
                        .append("CloneX 2.0.6\n")
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

    /**
     * The engine's foreground-service channel, recreated silent.
     * Same channel id, so ForgroundService keeps posting to it — just
     * quietly (IMPORTANCE_LOW: no status-bar icon, no sound). The service
     * itself is NOT stopped: the engine server process needs it alive.
     */
    private fun quietEngineNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            if (nm.getNotificationChannel(ENGINE_CHANNEL_ID) == null) return
            nm.deleteNotificationChannel(ENGINE_CHANNEL_ID)
            val quiet = NotificationChannel(
                ENGINE_CHANNEL_ID,
                "CloneX engine",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps the virtual engine running in the background."
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(quiet)
        } catch (e: Exception) {
            Log.w(TAG, "quietEngineNotificationChannel failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "CloneXApplication"

        /** Channel id the opensdk ForgroundService posts its notification on. */
        const val ENGINE_CHANNEL_ID = "hack_forground_id"

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
