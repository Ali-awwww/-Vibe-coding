package com.example.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class SymbolFileType {
    ANDROID_PROGUARD,
    ANDROID_R8,
    IOS_DSYM,
    JS_SOURCE_MAP,
    FLUTTER_SYMBOLS,
    RN_SOURCE_MAP,
    NATIVE_SYMBOLS
}

@Serializable
enum class SymbolicationStatus {
    SUCCESSFULLY_SYMBOLICATED,
    PARTIALLY_SYMBOLICATED,
    MISSING_DEBUG_SYMBOLS,
    BUILD_MISMATCH,
    SOURCE_REVISION_MISSING,
    PROCESSING_FAILED,
    PENDING
}

@Entity(tableName = "debug_symbols")
@Serializable
data class DebugSymbolFile(
    @PrimaryKey val id: String = "",
    val applicationId: String = "",
    val projectId: String = "",
    val type: SymbolFileType = SymbolFileType.ANDROID_PROGUARD,
    val appVersion: String = "",
    val buildId: String? = null,
    val uuid: String? = null,
    val fileName: String = "",
    val fileSize: Long = 0,
    val fileContent: String = "", // Base64 or reference
    val uploadTimestamp: Long = System.currentTimeMillis(),
    val status: SymbolicationStatus = SymbolicationStatus.PENDING
)

@Serializable
data class SymbolicatedFrame(
    val originalLine: String,
    val fileName: String?,
    val methodName: String?,
    val lineNumber: Int?,
    val libraryName: String?,
    val isSystem: Boolean = false
)

@Serializable
data class SymbolicationReport(
    val status: SymbolicationStatus,
    val frames: List<SymbolicatedFrame>,
    val error: String? = null
)
