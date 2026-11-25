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

    override suspend fun uploadDebugSymbol(file: DebugSymbolFile): Result<Unit> = runCatchingCancellable {
        require(file.applicationId.isNotBlank()) { "An application is required." }
        require(file.projectId.isNotBlank()) { "A project is required." }
        require(file.appVersion.isNotBlank()) { "An application version is required." }
        require(file.fileName.endsWith(".txt", ignoreCase = true) || file.fileName.endsWith(".map", ignoreCase = true)) {
            "Only text ProGuard/R8 mapping files are supported."
        }
        require(file.fileSize in 1L..MAX_MAPPING_FILE_BYTES) { "Mapping file size is outside the supported limit." }
        require(file.type == SymbolFileType.ANDROID_PROGUARD || file.type == SymbolFileType.ANDROID_R8) {
            "This client currently supports Android ProGuard/R8 mapping files only."
        }

        val application = crashDao.getApplicationById(file.applicationId)
            ?: throw IllegalArgumentException("The selected application does not exist.")
        require(application.projectId == file.projectId) {
            "The selected application does not belong to this project."
        }

        val parsed = ProguardMappingParser.parse(file.fileContent)
        require(parsed.isNotEmpty()) { "The selected file is not a valid ProGuard/R8 mapping." }

        val newFile = if (file.id.isEmpty()) file.copy(id = UUID.randomUUID().toString()) else file
        crashDao.insertDebugSymbol(newFile.copy(status = SymbolicationStatus.PENDING))
    }

    override suspend fun deleteDebugSymbol(id: String): Result<Unit> = runCatchingCancellable {
        require(id.isNotBlank()) { "A symbol artifact ID is required." }
        crashDao.deleteDebugSymbol(id)
    }

    override suspend fun symbolicate(crashReport: CrashReport): Result<SymbolicationReport> = runCatchingCancellable {
        val matchingSymbols = crashDao.getMatchingDebugSymbols(crashReport.applicationId, crashReport.appVersion)
            .sortedByDescending { it.uploadTimestamp }

        if (matchingSymbols.isEmpty()) {
            return@runCatching SymbolicationReport(
                status = SymbolicationStatus.MISSING_DEBUG_SYMBOLS,
                frames = emptyList(),
                error = "No mapping files match application version ${crashReport.appVersion}."
            )
        }

        val selected = matchingSymbols.firstOrNull {
            it.type == SymbolFileType.ANDROID_PROGUARD || it.type == SymbolFileType.ANDROID_R8
        } ?: return@runCatching SymbolicationReport(
            status = SymbolicationStatus.PROCESSING_FAILED,
            frames = crashReport.stackTrace.lines().map(::unresolvedFrame),
            error = "The stored symbol format is not supported by this client."
        )

        val mappings = ProguardMappingParser.parse(selected.fileContent)
        if (mappings.isEmpty()) {
            crashDao.updateDebugSymbolStatus(selected.id, SymbolicationStatus.PROCESSING_FAILED)
            return@runCatching SymbolicationReport(
                status = SymbolicationStatus.PROCESSING_FAILED,
                frames = crashReport.stackTrace.lines().map(::unresolvedFrame),
                error = "The stored ProGuard/R8 mapping could not be parsed."
            )
        }

        val lines = crashReport.stackTrace.lines().filter { it.isNotBlank() }
        val frames = lines.map { line ->
            when {
                isSystemFrame(line) -> SymbolicatedFrame(line, null, null, null, "system", true)
                line.trimStart().startsWith("at ") ->
                    ProguardMappingParser.resolveFrame(line, mappings) ?: unresolvedFrame(line)
                else -> unresolvedFrame(line)
            }
        }
        val applicationFrames = frames.filter { it.originalLine.trimStart().startsWith("at ") && !it.isSystem }
        val resolvedCount = applicationFrames.count { it.libraryName != null && it.methodName != null }

        val status = when {
            applicationFrames.isEmpty() -> SymbolicationStatus.PROCESSING_FAILED
            resolvedCount == applicationFrames.size -> SymbolicationStatus.SUCCESSFULLY_SYMBOLICATED
            resolvedCount > 0 -> SymbolicationStatus.PARTIALLY_SYMBOLICATED
            else -> SymbolicationStatus.PROCESSING_FAILED
        }
        crashDao.updateDebugSymbolStatus(selected.id, status)

        SymbolicationReport(
            status = status,
            frames = frames,
            error = when (status) {
                SymbolicationStatus.PARTIALLY_SYMBOLICATED -> "Some application frames could not be resolved."
                SymbolicationStatus.PROCESSING_FAILED -> "No application frames were resolved from the selected mapping."
                else -> null
            }
        )
    }

    private fun isSystemFrame(line: String): Boolean {
        val frame = line.trim()
        return frame.contains("android.") || frame.contains("java.") || frame.contains("kotlin.")
    }

    private fun unresolvedFrame(line: String) =
        SymbolicatedFrame(line, null, null, null, null, isSystemFrame(line))

    private companion object {
        const val MAX_MAPPING_FILE_BYTES = 10L * 1024L * 1024L
    }
}
