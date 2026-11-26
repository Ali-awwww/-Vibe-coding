package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics

private const val TAG = "AppCheckInit"
private const val CRASHLYTICS_TAG = "CrashlyticsInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun ensureFirebaseInitialized(context: Context): FirebaseApp? {
    return try {
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            FirebaseApp.getInstance()
        } else {
            FirebaseApp.initializeApp(context)?.also {
                Log.d(TAG, "FirebaseApp initialized from system resources.")
            }
        }
    } catch (e: Throwable) {
        Log.w(TAG, "Firebase initialization warning: ${e.message}", e)
        null
    }
}

fun isFirebaseConfigured(context: Context): Boolean {
    val app = ensureFirebaseInitialized(context) ?: return false
    val projectId = app.options.projectId
    return !projectId.isNullOrBlank() &&
        projectId != "remixed-project-id" &&
        projectId != "placeholder"
}

fun configureCrashlytics(context: Context) {
    runCatching {
        if (ensureFirebaseInitialized(context) == null) return@runCatching
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.isCrashlyticsCollectionEnabled = true
        crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME)
        crashlytics.setCustomKey("application_id", BuildConfig.APPLICATION_ID)
    }.onFailure { error ->
        Log.w(CRASHLYTICS_TAG, "Crashlytics initialization skipped: ${error.message}")
    }
}

fun setCrashlyticsUserId(userId: String?) {
    runCatching {
        FirebaseCrashlytics.getInstance().setUserId(userId.orEmpty())
    }
}

fun recordCrashlyticsNonFatal(error: Throwable, contextMessage: String? = null) {
    runCatching {
        val crashlytics = FirebaseCrashlytics.getInstance()
        if (!contextMessage.isNullOrBlank()) {
            crashlytics.log(contextMessage)
        }
        crashlytics.recordException(error)
    }
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        val app = FirebaseApp.getApps(context).firstOrNull() ?: run {
            Log.e(TAG, "FirebaseApp is not configured; skipping App Check installation.")
            return
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = app.persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // Synchronously commit before DebugAppCheckProviderFactory reads the secret.
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            val resId = context.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES,
                "string",
                context.packageName
            )
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                }
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
