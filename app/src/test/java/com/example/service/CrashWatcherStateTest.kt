package com.example.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CrashWatcherStateTest {

    @Before
    fun resetState() {
        CrashWatcherState.onProcessStartResult(processStarted = false)
        CrashWatcherState.setError(null)
    }

    @After
    fun clearState() {
        CrashWatcherState.onProcessStartResult(processStarted = false)
        CrashWatcherState.setError(null)
    }

    @Test
    fun failedProcessStartNeverReportsMonitoringAsActive() {
        CrashWatcherState.onProcessStartResult(
            processStarted = false,
            packages = setOf("com.example.target"),
            startedAt = 123L
        )

        assertFalse(CrashWatcherState.isMonitoring.value)
        assertTrue(CrashWatcherState.monitoredPackages.value.isEmpty())
        assertNull(CrashWatcherState.startTime.value)
    }

    @Test
    fun processStartWithoutSelectedPackagesCannotReportMonitoringAsActive() {
        CrashWatcherState.onProcessStartResult(
            processStarted = true,
            packages = emptySet(),
            startedAt = 456L
        )

        assertFalse(CrashWatcherState.isMonitoring.value)
        assertTrue(CrashWatcherState.monitoredPackages.value.isEmpty())
        assertNull(CrashWatcherState.startTime.value)
    }

    @Test
    fun confirmedProcessStartPublishesOnlyTheSelectedPackages() {
        val selected = setOf("com.example.one", "com.example.two")

        CrashWatcherState.onProcessStartResult(
            processStarted = true,
            packages = selected,
            startedAt = 789L
        )

        assertTrue(CrashWatcherState.isMonitoring.value)
        assertEquals(selected, CrashWatcherState.monitoredPackages.value)
        assertEquals(789L, CrashWatcherState.startTime.value)
    }
}
