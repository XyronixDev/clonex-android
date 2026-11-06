package com.clonex.space

import android.app.Application
import android.content.Context
import android.util.Log
import com.hack.opensdk.HackApi
import com.hack.opensdk.HackApplication

/**
 * CloneX v2.0 application — the MultiApp opensdk (HackApi) virtual engine
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
 */
class CloneXApplication : HackApplication() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        instance = this
    }

    override fun onCreate() {
        super.onCreate()
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

    companion object {
        private const val TAG = "CloneXApplication"

        @Volatile
        var instance: CloneXApplication? = null
            private set
    }
}
