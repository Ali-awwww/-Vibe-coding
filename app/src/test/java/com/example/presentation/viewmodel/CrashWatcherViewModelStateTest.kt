package com.example.presentation.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashWatcherViewModelStateTest {

    @Test
    fun appLoadFailureStopsLoadingAndPreservesLastKnownApps() {
        val app = MonitoredAppItem(
            packageName = "com.example.target",
            appName = "Target",
            versionName = "1.0",
            isSystemApp = false
        )
        val initial = CrashWatcherUiState(
            installedApps = listOf(app),
            filteredApps = listOf(app),
            isLoadingApps = true
        )

        val failed = initial.asAppsLoadFailure()

        assertFalse(failed.isLoadingApps)
        assertTrue(failed.appsLoadError)
        assertEquals(listOf(app), failed.installedApps)
        assertEquals(listOf(app), failed.filteredApps)
    }
}
