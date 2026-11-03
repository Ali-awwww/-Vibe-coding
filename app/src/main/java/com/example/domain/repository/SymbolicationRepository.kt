package com.example.domain.repository

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

interface SymbolicationRepository {
    fun getDebugSymbols(projectId: String): Flow<List<DebugSymbolFile>>
    suspend fun uploadDebugSymbol(file: DebugSymbolFile): Result<Unit>
    suspend fun deleteDebugSymbol(id: String): Result<Unit>
    
    suspend fun symbolicate(crashReport: CrashReport): Result<SymbolicationReport>
}
