package com.example.data.repository

import com.example.data.local.CrashDao
import com.example.domain.model.CrashReport
import com.example.domain.model.DebugSymbolFile
import com.example.domain.model.SymbolFileType
import com.example.domain.model.SymbolicationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class SymbolicationCancellationTest {
    @Test
    fun uploadDeleteAndSymbolicatePropagateCancellation() = runBlocking {
        val upload = runCatching {
            SymbolicationRepositoryImpl(cancellationDao("getApplicationById"))
                .uploadDebugSymbol(symbolFile())
        }
        assertTrue(
            "Uploading symbols must propagate cancellation instead of returning Result.failure.",
            upload.exceptionOrNull() is CancellationException
        )

        val delete = runCatching {
            SymbolicationRepositoryImpl(cancellationDao("deleteDebugSymbol"))
                .deleteDebugSymbol("mapping-1")
        }
        assertTrue(
            "Deleting symbols must propagate cancellation instead of returning Result.failure.",
            delete.exceptionOrNull() is CancellationException
        )

        val symbolicate = runCatching {
            SymbolicationRepositoryImpl(cancellationDao("getMatchingDebugSymbols"))
                .symbolicate(CrashReport(id = "crash-1", applicationId = "app-1", appVersion = "1.0"))
        }
        assertTrue(
            "Symbolication must propagate cancellation instead of returning Result.failure.",
            symbolicate.exceptionOrNull() is CancellationException
        )
    }

    private fun cancellationDao(methodName: String): CrashDao =
        Proxy.newProxyInstance(
            CrashDao::class.java.classLoader,
            arrayOf(CrashDao::class.java)
        ) { _, method, _ ->
            if (method.name == methodName) {
                throw CancellationException("Operation cancelled for test.")
            }
            throw AssertionError("Unexpected DAO call: ${method.name}")
        } as CrashDao

    private fun symbolFile() = DebugSymbolFile(
        id = "mapping-1",
        applicationId = "app-1",
        projectId = "project-1",
        type = SymbolFileType.ANDROID_R8,
        appVersion = "1.0",
        fileName = "mapping.txt",
        fileSize = 1L,
        fileContent = "mapping",
        status = SymbolicationStatus.PENDING
    )
}
