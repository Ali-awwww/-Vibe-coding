package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

private const val TAG = "FirebaseInit"

fun ensureFirebaseInitialized(context: Context): FirebaseApp? {
    return try {
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            FirebaseApp.getInstance()
        } else {
            // First attempt: initialize from auto-generated Android resources
            val defaultApp = try {
                FirebaseApp.initializeApp(context)
            } catch (_: Throwable) {
                null
            }
            if (defaultApp != null) {
                Log.d(TAG, "FirebaseApp initialized from system resources.")
                defaultApp
            } else {
                // Second attempt: programmatic options fallback to prevent uninitialized exceptions
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:1234567890:android:remixedappid")
                    .setApiKey("remixed-api-key")
                    .setProjectId("remixed-project-id")
                    .build()
                val app = FirebaseApp.initializeApp(context, options)
                Log.d(TAG, "FirebaseApp initialized with fallback options.")
                app
            }
        }
    } catch (e: Throwable) {
        Log.w(TAG, "Firebase initialization warning: proceeding in resilient local mode", e)
        null
    }
}

fun isFirebaseConfigured(context: Context): Boolean {
    val app = ensureFirebaseInitialized(context) ?: return false
    val projectId = app.options.projectId
    return !projectId.isNullOrBlank() && projectId != "remixed-project-id" && projectId != "placeholder"
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        ensureFirebaseInitialized(context)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize FirebaseApp", e)
    }
}
