package com.example.data.remote.supabase

import android.content.Context
import com.example.util.SecurityUtils
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Stores the Supabase refresh/access token pair encrypted with an Android Keystore key.
 * The encrypted preference value is excluded from Android backup by the app backup rules.
 */
class SupabaseSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val adapter = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
        .adapter(SupabaseStoredSession::class.java)

    @Synchronized
    fun read(): SupabaseStoredSession? {
        val encrypted = preferences.getString(SESSION_KEY, null) ?: return null
        return runCatching {
            adapter.fromJson(SecurityUtils.decryptSecret(encrypted))
        }.getOrElse {
            clear()
            null
        }
    }

    @Synchronized
    fun write(session: SupabaseStoredSession) {
        val serialized = adapter.toJson(session)
        val encrypted = SecurityUtils.encryptSecret(serialized)
        check(preferences.edit().putString(SESSION_KEY, encrypted).commit()) {
            "تعذر حفظ جلسة Supabase بأمان على هذا الجهاز."
        }
    }

    @Synchronized
    fun clear() {
        check(preferences.edit().remove(SESSION_KEY).commit()) {
            "تعذر حذف جلسة Supabase المحلية."
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "supabase_secure_session"
        const val SESSION_KEY = "encrypted_session_v1"
    }
}
