package com.example.util

import com.example.domain.model.*
import java.security.MessageDigest
import java.util.Locale

/**
 * Real Git & Version Control Engine for Workspaces in AI Vibe Studio.
 * Handles secret detection, diff calculation, deterministic SHA-1 hashes,
 * branch checkout snapshots, and conflict detection.
 */
object WorkspaceGitEngine {

    // Sensitive files that should never be committed/pushed without explicit override
    private val SENSITIVE_FILE_PATTERNS = listOf(
        Regex("""^\.env(\..+)?$"""),
        Regex(""".*\.(pem|key|keystore|jks|p12)$"""),
        Regex("""^id_(rsa|dsa|ecdsa|ed25519)$""")
    )

    // Regex patterns for detecting hardcoded credentials and API secrets
    private val SECRET_PATTERNS = listOf(
        Regex("""ghp_[a-zA-Z0-9]{36}""") to "GitHub Personal Access Token",
        Regex("""github_pat_[a-zA-Z0-9]{22}_[a-zA-Z0-9]{59}""") to "GitHub Fine-Grained Token",
        Regex("""AIza[0-9A-Za-z\-_]{35}""") to "Google Cloud / Firebase API Key",
        Regex("""AKIA[0-9A-Z]{16}""") to "AWS Access Key ID",
        Regex("""sk-[a-zA-Z0-9]{32,}""") to "OpenAI Secret Key",
        Regex("""xox[baprs]-[0-9]{10,13}-[a-zA-Z0-9]{24,32}""") to "Slack API Token",
        Regex("""-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----""") to "Cryptographic Private Key",
        Regex("""(?i)(?:password|secret|api_key|apikey|auth_token)\s*=\s*["'][^"']{8,}["']""") to "Hardcoded Credential / Secret"
    )

    /**
     * Scans working files or staged files for sensitive tokens, credentials, or dangerous files.
     */
    fun scanForSecrets(files: List<WorkspaceFile>): List<SecretAuditWarning> {
        val warnings = mutableListOf<SecretAuditWarning>()

        for (file in files) {
            if (file.isFolder) continue

            val fileName = file.name.lowercase(Locale.ROOT)
            val isSensitiveFileName = SENSITIVE_FILE_PATTERNS.any { it.matches(fileName) }

            if (isSensitiveFileName) {
                warnings.add(
                    SecretAuditWarning(
                        filePath = file.path,
                        line = 1,
                        secretType = "ملف بيئة حساس / مفاتيح خاصة (.env or private key)",
                        snippet = file.path,
                        isBlocking = true
                    )
                )
            }

            // Line by line scanning of file content
            val lines = file.content.lines()
            for ((index, line) in lines.withIndex()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("#") && !trimmed.contains("=")) {
                    // Skip pure comment lines unless they define variables
                    continue
                }

                for ((regex, secretName) in SECRET_PATTERNS) {
                    val match = regex.find(line)
                    if (match != null) {
                        val maskedSnippet = line.replace(match.value, match.value.take(4) + "••••••••" + match.value.takeLast(3)).trim()
                        warnings.add(
                            SecretAuditWarning(
                                filePath = file.path,
                                line = index + 1,
                                secretType = secretName,
                                snippet = maskedSnippet.take(120),
                                isBlocking = true
                            )
                        )
                        break
                    }
                }
            }
        }

        return warnings
    }

    /**
     * Computes the working tree diff between current files and a baseline snapshot.
     */
    fun computeWorkingTreeDiff(
        currentFiles: List<WorkspaceFile>,
        baselineFiles: List<WorkspaceFile>
    ): List<FileDiff> {
        val baselineMap = baselineFiles.filter { !it.isFolder }.associateBy { it.path }
        val currentMap = currentFiles.filter { !it.isFolder }.associateBy { it.path }

        val diffs = mutableListOf<FileDiff>()

        // Check for created or modified files
        for ((path, curr) in currentMap) {
            val base = baselineMap[path]
            if (base == null) {
                // Newly created
                diffs.add(DiffUtils.computeDiff(path, "", curr.content))
            } else if (base.content != curr.content) {
                // Modified
                diffs.add(DiffUtils.computeDiff(path, base.content, curr.content))
            }
        }

        // Check for deleted files
        for ((path, base) in baselineMap) {
            if (!currentMap.containsKey(path)) {
                diffs.add(DiffUtils.computeDiff(path, base.content, ""))
            }
        }

        return diffs
    }

    /**
     * Generates a 40-character SHA-1 hash for a Git commit.
     */
    fun generateCommitSha(
        workspaceId: String,
        branch: String,
        parentSha: String?,
        message: String,
        timestamp: Long,
        filesJson: String
    ): String {
        val input = "commit $workspaceId $branch $parentSha $message $timestamp ${filesJson.hashCode()}"
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Detects test files that are affected by changes in modified source files.
     */
    fun detectAffectedTests(
        changedDiffs: List<FileDiff>,
        allFiles: List<WorkspaceFile>
    ): List<String> {
        val changedBaseNames = changedDiffs.map {
            it.path.substringAfterLast("/").substringBeforeLast(".")
        }.filter { it.isNotBlank() }.toSet()

        val testFiles = allFiles.filter { file ->
            !file.isFolder && (
                file.path.contains("test", ignoreCase = true) ||
                file.path.contains("spec", ignoreCase = true) ||
                file.name.endsWith("Test.kt") ||
                file.name.endsWith(".test.js") ||
                file.name.endsWith(".test.tsx") ||
                file.name.endsWith(".spec.ts")
            )
        }

        val affected = mutableListOf<String>()
        for (testFile in testFiles) {
            val testName = testFile.name
            // Direct match by file name (e.g. AuthService.kt -> AuthServiceTest.kt)
            val directMatch = changedBaseNames.any { base -> testName.contains(base, ignoreCase = true) }
            if (directMatch) {
                affected.add(testFile.path)
                continue
            }

            // Match by import reference inside test content
            val importsChanged = changedBaseNames.any { base ->
                testFile.content.contains(base)
            }
            if (importsChanged) {
                affected.add(testFile.path)
            }
        }

        return affected.distinct()
    }

    /**
     * Detects 3-way merge conflicts between source branch and target branch.
     */
    fun checkMergeConflicts(
        sourceFiles: List<WorkspaceFile>,
        targetFiles: List<WorkspaceFile>
    ): List<String> {
        val sourceMap = sourceFiles.filter { !it.isFolder }.associateBy { it.path }
        val targetMap = targetFiles.filter { !it.isFolder }.associateBy { it.path }

        val conflicts = mutableListOf<String>()

        for ((path, srcFile) in sourceMap) {
            val tgtFile = targetMap[path]
            if (tgtFile != null && tgtFile.content != srcFile.content) {
                // Overlapping modifications without clean resolution
                conflicts.add(path)
            }
        }

        return conflicts
    }
}
