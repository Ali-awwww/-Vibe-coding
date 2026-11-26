package com.example.util.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import rikka.shizuku.Shizuku

object ShizukuManager {
    private const val TAG = "ShizukuManager"
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE"
    const val GITHUB_RELEASES_URL = "https://github.com/RikkaApps/Shizuku/releases"
    const val ADB_START_COMMAND = "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh"

    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (e: Throwable) {
            Log.w(TAG, "Error checking Shizuku package: ${e.message}")
            false
        }
    }

    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            Log.d(TAG, "Shizuku pingBinder failed: ${e.message}")
            false
        }
    }

    fun checkPermission(): Boolean {
        if (!isShizukuRunning()) return false
        return try {
            if (Shizuku.isPreV11()) {
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error checking Shizuku permission: ${e.message}")
            false
        }
    }

    fun requestPermission(requestCode: Int) {
        if (!isShizukuRunning()) return
        try {
            Shizuku.requestPermission(requestCode)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
        }
    }

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.addRequestPermissionResultListener(listener)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to add Shizuku permission listener: ${e.message}")
        }
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.removeRequestPermissionResultListener(listener)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to remove Shizuku permission listener: ${e.message}")
        }
    }

    fun executeProcess(cmd: Array<String>): Process? {
        if (!checkPermission()) {
            Log.w(TAG, "Cannot execute command, Shizuku permission not granted")
            return null
        }
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            method.invoke(null, cmd, null, null) as? Process
        } catch (e: Throwable) {
            // Never fall back to the app UID: it does not have Shizuku's privileges and
            // would make a failed privileged operation look like a successful launch.
            Log.e(TAG, "Error starting Shizuku remote process: ${e.message}", e)
            null
        }
    }

    fun openPlayStore(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openGitHub(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
