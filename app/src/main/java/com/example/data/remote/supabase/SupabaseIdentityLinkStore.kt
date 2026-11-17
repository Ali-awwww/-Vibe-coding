package com.example.data.remote.supabase

import android.annotation.SuppressLint
import android.content.Context
import com.example.util.SecurityUtils
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Locale

@JsonClass(generateAdapter = true)
data class SupabaseIdentityLink(
    @Json(name = "firebase_user_id") val firebaseUserId: String,
    @Json(name = "supabase_user_id") val supabaseUserId: String,
    val email: String
)

interface SupabaseIdentityLinkProvider {
    fun getSupabaseUserId(firebaseUserId: String): String?
}

interface SupabaseIdentityLinkStore : SupabaseIdentityLinkProvider {
    fun write(firebaseUserId: String, supabaseUserId: String, email: String)
    fun clear()
}

/**
 * Stores only provider subject IDs and the Google email; OAuth tokens remain in their existing
 * provider-specific stores. The mapping is encrypted with the Android Keystore.
 */
class EncryptedSupabaseIdentityLinkStore(
    context: Context,
    private val encryptSecret: (String) -> String = SecurityUtils::encryptSecret,
    private val decryptSecret: (String) -> String = SecurityUtils::decryptSecret
) : SupabaseIdentityLinkStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val adapter = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
        .adapter(SupabaseIdentityLink::class.java)

    @Synchronized
    override fun getSupabaseUserId(firebaseUserId: String): String? {
        if (firebaseUserId.isBlank()) return null
        return read()?.takeIf { it.firebaseUserId == firebaseUserId }?.supabaseUserId
    }

    @SuppressLint("UseKtx")
    @Synchronized
    override fun write(firebaseUserId: String, supabaseUserId: String, email: String) {
        val normalizedFirebaseId = firebaseUserId.trim()
        val normalizedSupabaseId = supabaseUserId.trim()
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        require(normalizedFirebaseId.isNotBlank()) { "Firebase user ID is required." }
        require(normalizedSupabaseId.isNotBlank()) { "Supabase user ID is required." }
        require(normalizedEmail.isNotBlank()) { "A Google email is required for identity linking." }

        val encrypted = encryptSecret(
            adapter.toJson(
                SupabaseIdentityLink(
                    firebaseUserId = normalizedFirebaseId,
                    supabaseUserId = normalizedSupabaseId,
                    email = normalizedEmail
                )
            )
        )
        // A synchronous commit is required before the matching app identity becomes visible.
        check(preferences.edit().putString(SESSION_KEY, encrypted).commit()) {
            "Unable to persist the provider identity link securely."
        }
    }

    @SuppressLint("UseKtx")
    @Synchronized
    override fun clear() {
        // Synchronous removal prevents a stale provider mapping after account switch or logout.
        check(preferences.edit().remove(SESSION_KEY).commit()) {
            "Unable to clear the provider identity link."
        }
    }

    private fun read(): SupabaseIdentityLink? {
        val encrypted = preferences.getString(SESSION_KEY, null) ?: return null
        return runCatching {
            adapter.fromJson(decryptSecret(encrypted))
                ?: throw IllegalStateException("The stored provider identity link is empty.")
        }.getOrElse {
            clear()
            null
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "supabase_identity_link"
        const val SESSION_KEY = "encrypted_identity_link_v1"
    }
}
