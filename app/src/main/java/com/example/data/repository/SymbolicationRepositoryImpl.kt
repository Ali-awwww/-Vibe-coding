package com.example.data.repository

import com.example.data.local.CrashDao
import com.example.domain.model.*
import com.example.domain.repository.SymbolicationRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class SymbolicationRepositoryImpl(
    private val crashDao: CrashDao
) : SymbolicationRepository {

    override fun getDebugSymbols(projectId: String): Flow<List<DebugSymbolFile>> =
        crashDao.getDebugSymbolsForProject(projectId)

    override suspend fun uploadDebugSymbol(file: DebugSymbolFile): Result<Unit> = runCatching {
        val newFile = if (file.id.isEmpty()) file.copy(id = UUID.randomUUID().toString()) else file
        crashDao.insertDebugSymbol(newFile)
    }

    override suspend fun deleteDebugSymbol(id: String): Result<Unit> = runCatching {
        crashDao.deleteDebugSymbol(id)
    }

    override suspend fun symbolicate(crashReport: CrashReport): Result<SymbolicationReport> = runCatching {
        val matchingSymbols = crashDao.getMatchingDebugSymbols(crashReport.applicationId, crashReport.appVersion)
        
        if (matchingSymbols.isEmpty()) {
            return@runCatching SymbolicationReport(
                status = SymbolicationStatus.MISSING_DEBUG_SYMBOLS,
                frames = emptyList(),
                error = "No mapping files found for version ${crashReport.appVersion}"
            )
        }

        // Simulation: If stack trace contains "com.example" but is obfuscated (a, b, c)
        // We'll "de-obfuscate" it for the demo
        val lines = crashReport.stackTrace.lines()
        val symbolicatedFrames = lines.map { line ->
            if (line.contains("at ") && line.contains(".a(")) {
                SymbolicatedFrame(
                    originalLine = line,
                    fileName = "MainActivity.kt",
                    methodName = "onCreate",
                    lineNumber = 42,
                    libraryName = "app",
                    isSystem = false
                )
            } else if (line.contains("android.") || line.contains("java.")) {
                SymbolicatedFrame(
                    originalLine = line,
                    fileName = null,
                    methodName = null,
                    lineNumber = null,
                    libraryName = "system",
                    isSystem = true
                )
            } else {
                SymbolicatedFrame(
                    originalLine = line,
                    fileName = null,
                    methodName = null,
                    lineNumber = null,
                    libraryName = null,
                    isSystem = false
                )
            }
        }

        SymbolicationReport(
            status = SymbolicationStatus.SUCCESSFULLY_SYMBOLICATED,
            frames = symbolicatedFrames
        )
    }
}
