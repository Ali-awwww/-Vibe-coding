package com.example.util.logcat

import com.example.domain.model.ErrorCategory
import com.example.domain.model.Severity
import java.util.regex.Pattern

data class ParsedCrash(
    val packageName: String,
    val exceptionType: String,
    val message: String,
    val stackTrace: String,
    val fullContext: String,
    val timestamp: Long = System.currentTimeMillis(),
    val pid: Int? = null,
    val threadName: String? = null,
    val causedByChain: List<String> = emptyList(),
    val category: ErrorCategory = ErrorCategory.FATAL_EXCEPTION,
    val severity: Severity = Severity.CRITICAL
)

object LogcatCrashParser {

    private val PROCESS_REGEX = Pattern.compile("Process:\\s*([a-zA-Z0-9_.]+)", Pattern.CASE_INSENSITIVE)
    private val PID_REGEX = Pattern.compile("PID:\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
    private val FATAL_SIGNAL_REGEX = Pattern.compile(
        "Fatal signal (\\d+) \\(([^)]+)\\)(?:, code (\\d+) \\(([^)]+)\\))?.*?(?:in tid \\d+ \\(([a-zA-Z0-9_.]+)\\)|pid \\d+ \\(([a-zA-Z0-9_.]+)\\)|>>>\\s*([a-zA-Z0-9_.]+)\\s*<<<)",
        Pattern.CASE_INSENSITIVE
    )
    private val ANR_REGEX = Pattern.compile("ANR in\\s+([a-zA-Z0-9_.]+)", Pattern.CASE_INSENSITIVE)
    private val REASON_REGEX = Pattern.compile("Reason:\\s*(.+)", Pattern.CASE_INSENSITIVE)
    private val THREADTIME_REGEX = Pattern.compile(
        "^(?:\\d{2}-\\d{2}|\\d{4}-\\d{2}-\\d{2})\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+[^:]*:(.*)$"
    )
    private val BRIEF_REGEX = Pattern.compile(
        "^[VDIWEFA]/[^:]*:(.*)$"
    )

    fun extractLogPayload(line: String): String {
        val ttMatcher = THREADTIME_REGEX.matcher(line)
        if (ttMatcher.find()) {
            return ttMatcher.group(1)?.trim().orEmpty()
        }
        val briefMatcher = BRIEF_REGEX.matcher(line)
        if (briefMatcher.find()) {
            return briefMatcher.group(1)?.trim().orEmpty()
        }
        return line.trim()
    }

    fun parseLines(
        lines: List<String>,
        monitoredPackages: Set<String>
    ): List<ParsedCrash> {
        if (lines.isEmpty() || monitoredPackages.isEmpty()) return emptyList()

        val crashes = mutableListOf<ParsedCrash>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // 1. FATAL EXCEPTION Check
            if (line.contains("FATAL EXCEPTION", ignoreCase = true)) {
                val parsed = parseFatalException(lines, i, monitoredPackages)
                if (parsed != null) {
                    crashes.add(parsed)
                }
            }
            // 2. Fatal signal (Native crash)
            else if (line.contains("Fatal signal", ignoreCase = true)) {
                val parsed = parseFatalSignal(lines, i, monitoredPackages)
                if (parsed != null) {
                    crashes.add(parsed)
                }
            }
            // 3. ANR Check
            else if (line.contains("ANR in ", ignoreCase = true)) {
                val parsed = parseAnr(lines, i, monitoredPackages)
                if (parsed != null) {
                    crashes.add(parsed)
                }
            }

            i++
        }

        return crashes.distinctBy { "${it.packageName}:${it.exceptionType}:${it.message.take(64)}" }
    }

    private fun parseFatalException(
        lines: List<String>,
        startIndex: Int,
        monitoredPackages: Set<String>
    ): ParsedCrash? {
        val headerLine = lines[startIndex]
        val threadName = headerLine.substringAfter("FATAL EXCEPTION:").trim().takeIf { it.isNotBlank() }

        var targetPkg: String? = null
        var pid: Int? = null
        var exceptionType = ""
        var exceptionMessage = ""
        val traceLines = mutableListOf<String>()
        val causedByList = mutableListOf<String>()

        var scanIndex = startIndex + 1
        val maxScan = minOf(lines.size, startIndex + 120)

        while (scanIndex < maxScan) {
            val current = lines[scanIndex]
            val stripped = current.trim()
            val payload = extractLogPayload(current)

            // Process line
            if (payload.startsWith("Process:", ignoreCase = true) || payload.contains("Process: ")) {
                val matcher = PROCESS_REGEX.matcher(payload)
                if (matcher.find()) {
                    targetPkg = matcher.group(1)?.trim()
                }
                val pidMatcher = PID_REGEX.matcher(payload)
                if (pidMatcher.find()) {
                    pid = pidMatcher.group(1)?.toIntOrNull()
                }
                scanIndex++
                continue
            }

            // Exception declaration line (e.g. java.lang.NullPointerException: msg)
            if (exceptionType.isEmpty() && (payload.contains("Exception") || payload.contains("Error"))) {
                val colonIdx = payload.indexOf(':')
                if (colonIdx != -1) {
                    val candidateType = payload.substring(0, colonIdx).trim().substringAfterLast(' ')
                    if (candidateType.contains("Exception") || candidateType.contains("Error")) {
                        exceptionType = candidateType
                        exceptionMessage = payload.substring(colonIdx + 1).trim()
                        traceLines.add(payload)
                        scanIndex++
                        continue
                    }
                } else {
                    val candidateType = payload.substringAfterLast(' ').trim()
                    if (candidateType.contains("Exception") || candidateType.contains("Error")) {
                        exceptionType = candidateType
                        exceptionMessage = ""
                        traceLines.add(payload)
                        scanIndex++
                        continue
                    }
                }
            }

            // Stack trace frames or Caused by lines
            val trimmedPayload = payload.trimStart()
            if (trimmedPayload.startsWith("at ") || trimmedPayload.startsWith("Caused by:") || trimmedPayload.startsWith("... ")) {
                traceLines.add(trimmedPayload)
                if (trimmedPayload.startsWith("Caused by:")) {
                    causedByList.add(trimmedPayload.substringAfter("Caused by:").trim())
                }
                scanIndex++
                continue
            }

            // If we already captured exception and hit a non-trace line, stop scan
            if (exceptionType.isNotEmpty() && traceLines.size >= 2) {
                break
            }

            scanIndex++
        }

        // If no explicit Process line, check if any monitored package is mentioned in header or trace
        if (targetPkg == null) {
            targetPkg = monitoredPackages.firstOrNull { pkg ->
                lines.subList(startIndex, minOf(lines.size, startIndex + 20)).any { it.contains(pkg) }
            }
        }

        if (targetPkg == null || targetPkg !in monitoredPackages) {
            return null
        }

        if (exceptionType.isBlank()) {
            exceptionType = "java.lang.RuntimeException"
        }
        if (exceptionMessage.isBlank()) {
            exceptionMessage = "FATAL EXCEPTION in $targetPkg"
        }

        val stackTrace = traceLines.joinToString("\n").ifBlank { headerLine }
        val context = captureSurroundingContext(lines, startIndex, scanIndex)

        val category = when {
            exceptionType.contains("NullPointer", ignoreCase = true) -> ErrorCategory.NULL_POINTER_EXCEPTION
            exceptionType.contains("OutOfMemory", ignoreCase = true) -> ErrorCategory.OUT_OF_MEMORY
            exceptionType.contains("Network", ignoreCase = true) -> ErrorCategory.NETWORK_ERROR
            else -> ErrorCategory.FATAL_EXCEPTION
        }

        return ParsedCrash(
            packageName = targetPkg,
            exceptionType = exceptionType,
            message = exceptionMessage,
            stackTrace = stackTrace,
            fullContext = context,
            pid = pid,
            threadName = threadName,
            causedByChain = causedByList,
            category = category,
            severity = Severity.CRITICAL
        )
    }

    private fun parseFatalSignal(
        lines: List<String>,
        startIndex: Int,
        monitoredPackages: Set<String>
    ): ParsedCrash? {
        val signalLine = lines[startIndex]
        var targetPkg: String? = null
        var signalName = "SIGSEGV"
        var signalCode = ""

        val matcher = FATAL_SIGNAL_REGEX.matcher(signalLine)
        if (matcher.find()) {
            val sigNum = matcher.group(1) ?: "11"
            val sigName = matcher.group(2) ?: "SIGSEGV"
            signalName = "Fatal signal $sigNum ($sigName)"
            val codeNum = matcher.group(3)
            val codeName = matcher.group(4)
            if (codeNum != null && codeName != null) {
                signalCode = "code $codeNum ($codeName)"
            }
            targetPkg = matcher.group(5) ?: matcher.group(6) ?: matcher.group(7)
        }

        var scanIndex = startIndex + 1
        val maxScan = minOf(lines.size, startIndex + 80)
        val backtraceLines = mutableListOf<String>()

        while (scanIndex < maxScan) {
            val cur = lines[scanIndex]
            val payload = extractLogPayload(cur).trimStart()
            if (targetPkg == null) {
                val pkgMatch = monitoredPackages.firstOrNull { cur.contains(it) }
                if (pkgMatch != null) targetPkg = pkgMatch
            }
            if (payload.startsWith("#") || payload.contains("backtrace:", ignoreCase = true)) {
                backtraceLines.add(payload)
            } else if (backtraceLines.isNotEmpty() && !cur.contains("DEBUG") && !cur.contains("***")) {
                break
            }
            scanIndex++
        }

        if (targetPkg == null || targetPkg !in monitoredPackages) {
            return null
        }

        val stackTrace = if (backtraceLines.isNotEmpty()) {
            backtraceLines.joinToString("\n")
        } else {
            signalLine
        }
        val context = captureSurroundingContext(lines, startIndex, scanIndex)

        return ParsedCrash(
            packageName = targetPkg,
            exceptionType = signalName,
            message = signalCode.ifBlank { "Native fatal signal crash in $targetPkg" },
            stackTrace = stackTrace,
            fullContext = context,
            category = ErrorCategory.NATIVE_CRASH,
            severity = Severity.CRITICAL
        )
    }

    private fun parseAnr(
        lines: List<String>,
        startIndex: Int,
        monitoredPackages: Set<String>
    ): ParsedCrash? {
        val anrLine = lines[startIndex]
        var targetPkg: String? = null

        val anrMatcher = ANR_REGEX.matcher(anrLine)
        if (anrMatcher.find()) {
            targetPkg = anrMatcher.group(1)?.trim()
        }

        if (targetPkg == null || targetPkg !in monitoredPackages) {
            return null
        }

        var reason = "Input dispatching timed out or main thread blocked"
        var scanIndex = startIndex + 1
        val maxScan = minOf(lines.size, startIndex + 40)
        val anrContext = mutableListOf(anrLine)

        while (scanIndex < maxScan) {
            val cur = lines[scanIndex].trim()
            anrContext.add(cur)
            val reasonMatcher = REASON_REGEX.matcher(cur)
            if (reasonMatcher.find()) {
                reason = reasonMatcher.group(1)?.trim() ?: reason
            }
            scanIndex++
        }

        val context = captureSurroundingContext(lines, startIndex, scanIndex)

        return ParsedCrash(
            packageName = targetPkg,
            exceptionType = "Application Not Responding (ANR)",
            message = reason,
            stackTrace = anrContext.joinToString("\n"),
            fullContext = context,
            category = ErrorCategory.ANR,
            severity = Severity.HIGH
        )
    }

    private fun captureSurroundingContext(
        lines: List<String>,
        crashStart: Int,
        crashEnd: Int,
        radius: Int = 25
    ): String {
        val from = maxOf(0, crashStart - radius)
        val to = minOf(lines.size, crashEnd + radius)
        return lines.subList(from, to).joinToString("\n")
    }
}
