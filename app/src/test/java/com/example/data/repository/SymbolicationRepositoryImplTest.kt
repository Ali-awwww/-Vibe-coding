package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.domain.model.Application
import com.example.domain.model.CrashReport
import com.example.domain.model.DebugSymbolFile
import com.example.domain.model.SymbolFileType
import com.example.domain.model.SymbolicationStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config
class SymbolicationRepositoryImplTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: SymbolicationRepositoryImpl
    private val mapping = """
        com.example.MainActivity -> a:
            1:1:void onCreate(android.os.Bundle):42:42 -> a
    """.trimIndent()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = SymbolicationRepositoryImpl(database.crashDao())
    }
    @After fun tearDown() { database.close() }

    @Test
    fun uploadRejectsApplicationFromAnotherProject() = runBlocking {
        database.crashDao().insertApplication(Application(id = "app-1", projectId = "project-actual", name = "App", packageName = "com.example.app"))
        val result = repository.uploadDebugSymbol(symbolFile(projectId = "project-requested"))
        assertTrue(result.isFailure)
    }

    @Test
    fun validMappingIsStoredAsPendingUntilItActuallySymbolicates() = runBlocking {
        database.crashDao().insertApplication(Application(id = "app-1", projectId = "project-1", name = "App", packageName = "com.example.app"))
        val result = repository.uploadDebugSymbol(symbolFile())
        assertTrue(result.isSuccess)
        val stored = database.crashDao().getMatchingDebugSymbols("app-1", "1.0").single()
        assertEquals(SymbolicationStatus.PENDING, stored.status)
    }

    @Test
    fun symbolicationUsesTheMappingInsteadOfFabricatingFrames() = runBlocking {
        database.crashDao().insertApplication(Application(id = "app-1", projectId = "project-1", name = "App", packageName = "com.example.app"))
        repository.uploadDebugSymbol(symbolFile())
        val crash = CrashReport(
            id = "crash-1",
            applicationId = "app-1",
            projectId = "project-1",
            exceptionType = "RuntimeException",
            message = "boom",
            stackTrace = "java.lang.RuntimeException: boom\n    at a.a(SourceFile:1)\n    at android.app.Activity.performCreate(Activity.java:1)",
            deviceModel = "test",
            osVersion = "test",
            appVersion = "1.0"
        )
        val result = repository.symbolicate(crash).getOrThrow()
        assertEquals(SymbolicationStatus.SUCCESSFULLY_SYMBOLICATED, result.status)
        assertEquals("com.example.MainActivity", result.frames[1].libraryName)
        assertEquals("onCreate", result.frames[1].methodName)
        assertEquals(42, result.frames[1].lineNumber)
        assertTrue(result.frames[2].isSystem)
    }

    private fun symbolFile(projectId: String = "project-1") = DebugSymbolFile(
        id = "mapping-1",
        applicationId = "app-1",
        projectId = projectId,
        type = SymbolFileType.ANDROID_R8,
        appVersion = "1.0",
        fileName = "mapping.txt",
        fileSize = mapping.toByteArray(Charsets.UTF_8).size.toLong(),
        fileContent = mapping,
        status = SymbolicationStatus.SUCCESSFULLY_SYMBOLICATED
    )
}
