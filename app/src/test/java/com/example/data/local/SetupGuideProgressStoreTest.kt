package com.example.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.IntegrationType
import com.example.domain.model.ProjectTechnology
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config
class SetupGuideProgressStoreTest {
    private lateinit var context: Context

    @Before
    fun clearPreferences() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("setup_guide_progress_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun selectionPersistsAcrossStoreInstancesAndIsScopedToApplication() {
        val store = SharedPreferencesSetupGuideProgressStore(context)
        store.save("project-1", "application-1", ProjectTechnology.ANDROID_KOTLIN, IntegrationType.BUGSNAG)

        val progress = SharedPreferencesSetupGuideProgressStore(context).load("project-1", "application-1")
        assertEquals(ProjectTechnology.ANDROID_KOTLIN, progress?.tech)
        assertEquals(IntegrationType.BUGSNAG, progress?.service)
        assertNull(SharedPreferencesSetupGuideProgressStore(context).load("project-1", "application-2"))
        assertNull(SharedPreferencesSetupGuideProgressStore(context).load("project-2", "application-1"))
    }

    @Test
    fun clearingPlatformKeepsProviderWithoutCreatingCompletion() {
        val store = SharedPreferencesSetupGuideProgressStore(context)
        store.save("project-1", "application-1", ProjectTechnology.ANDROID_JAVA, IntegrationType.SENTRY)
        store.save("project-1", "application-1", null, IntegrationType.SENTRY)

        val progress = store.load("project-1", "application-1")
        assertNull(progress?.tech)
        assertEquals(IntegrationType.SENTRY, progress?.service)
    }
}
