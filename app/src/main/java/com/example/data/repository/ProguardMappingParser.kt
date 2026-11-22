package com.example.data.repository

import com.example.domain.model.SymbolicatedFrame

internal object ProguardMappingParser {
    data class MethodMapping(
        val originalName: String,
        val obfuscatedName: String,
        val obfuscatedStartLine: Int?,
        val obfuscatedEndLine: Int?,
        val originalStartLine: Int?,
        val originalEndLine: Int?
    )

    data class ClassMapping(
        val originalName: String,
        val obfuscatedName: String,
        val methods: List<MethodMapping>
    )

    private val classPattern = Regex("""^(\S+)\s+->\s+(\S+):\s*$""")
    private val methodPattern = Regex(
        """^\s*(?:(\d+):(\d+):)?(?:.+?\s+)?([^\s(]+)\([^)]*\)(?::(\d+):(\d+))?\s+->\s+(\S+)\s*$"""
    )
    private val stackFramePattern = Regex("""^\s*at\s+([^\s(]+)\(([^)]*)\)\s*$""")

    fun parse(content: String): Map<String, ClassMapping> {
        val mappings = linkedMapOf<String, ClassMapping>()
        var originalClass: String? = null
        var obfuscatedClass: String? = null
        var methods = mutableListOf<MethodMapping>()

        fun flushClass() {
            val original = originalClass
            val obfuscated = obfuscatedClass
            if (original != null && obfuscated != null) {
                mappings[obfuscated] = ClassMapping(original, obfuscated, methods.toList())
            }
            originalClass = null
            obfuscatedClass = null
            methods = mutableListOf()
        }

        content.lineSequence().forEach { line ->
            if (line.isBlank() || line.trimStart().startsWith("#")) return@forEach
            if (!line.first().isWhitespace()) {
                val match = classPattern.matchEntire(line.trim())
                if (match != null) {
                    flushClass()
                    originalClass = match.groupValues[1]
                    obfuscatedClass = match.groupValues[2]
                }
            } else {
                val match = methodPattern.matchEntire(line)
                if (match != null && obfuscatedClass != null) {
                    methods += MethodMapping(
                        originalName = match.groupValues[3],
                        obfuscatedName = match.groupValues[6],
                        obfuscatedStartLine = match.groupValues[1].toIntOrNull(),
                        obfuscatedEndLine = match.groupValues[2].toIntOrNull(),
                        originalStartLine = match.groupValues[4].toIntOrNull(),
                        originalEndLine = match.groupValues[5].toIntOrNull()
                    )
                }
            }
        }
        flushClass()
        return mappings
    }

    fun resolveFrame(line: String, mappings: Map<String, ClassMapping>): SymbolicatedFrame? {
        val frame = stackFramePattern.matchEntire(line) ?: return null
        val qualifiedMethod = frame.groupValues[1]
        val separator = qualifiedMethod.lastIndexOf('.')
        if (separator <= 0 || separator == qualifiedMethod.lastIndex) return null

        val obfuscatedClass = qualifiedMethod.substring(0, separator)
        val obfuscatedMethod = qualifiedMethod.substring(separator + 1)
        val mapping = mappings[obfuscatedClass] ?: return null
        val location = frame.groupValues[2]
        val obfuscatedLine = location.substringAfterLast(':', "").toIntOrNull()
        val method = mapping.methods.firstOrNull { candidate ->
            candidate.obfuscatedName == obfuscatedMethod &&
                (obfuscatedLine == null ||
                    candidate.obfuscatedStartLine == null ||
                    candidate.obfuscatedEndLine == null ||
                    obfuscatedLine in candidate.obfuscatedStartLine..candidate.obfuscatedEndLine)
        }

        val originalLine = when {
            method?.originalStartLine != null &&
                method.obfuscatedStartLine != null &&
                obfuscatedLine != null ->
                method.originalStartLine + (obfuscatedLine - method.obfuscatedStartLine)
            method?.originalStartLine != null -> method.originalStartLine
            else -> null
        }

        return SymbolicatedFrame(
            originalLine = line,
            fileName = null,
            methodName = method?.originalName,
            lineNumber = originalLine,
            libraryName = mapping.originalName,
            isSystem = false
        )
    }
}
