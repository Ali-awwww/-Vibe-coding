package com.example.data.remote.supabase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.Base64
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupabaseIdentityLinkStoreTest {
    private lateinit var context: Context
    private lateinit var store: EncryptedSupabaseIdentityLinkStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("supabase_identity_link", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = EncryptedSupabaseIdentityLinkStore(
            context = context,
            encryptSecret = { Base64.getEncoder().encodeToString(it.toByteArray()) },
            decryptSecret = { String(Base64.getDecoder().decode(it)) }
        )
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun linkIsEncryptedAndOnlyResolvesForTheMatchingFirebaseSubject() {
        store.write("firebase-user-a", "supabase-user-a", "Owner@Example.com")

        assertEquals("supabase-user-a", store.getSupabaseUserId("firebase-user-a"))
        assertNull(store.getSupabaseUserId("firebase-user-b"))

        val encrypted = context.getSharedPreferences(
            "supabase_identity_link",
            Context.MODE_PRIVATE
        ).getString("encrypted_identity_link_v1", null)
        assertNotNull(encrypted)
        assertFalse(encrypted!!.contains("firebase-user-a"))
        assertFalse(encrypted.contains("supabase-user-a"))
        assertFalse(encrypted.contains("owner@example.com"))
    }

    @Test
    fun corruptedIdentityLinkIsClearedInsteadOfBeingReused() {
        context.getSharedPreferences("supabase_identity_link", Context.MODE_PRIVATE)
            .edit().putString("encrypted_identity_link_v1", "not-valid-base64").commit()

        assertNull(store.getSupabaseUserId("firebase-user-a"))
        assertNull(
            context.getSharedPreferences("supabase_identity_link", Context.MODE_PRIVATE)
                .getString("encrypted_identity_link_v1", null)
        )
    }
}
