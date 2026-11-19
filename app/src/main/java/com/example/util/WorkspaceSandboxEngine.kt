package com.example.util

import com.example.domain.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.UUID

object WorkspaceSandboxEngine {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    // Port allocation tracking
    private val allocatedPorts = mutableMapOf<String, Int>()

    /**
     * Inspects workspace files to accurately detect framework, start commands, configuration and port.
     * Adheres strictly to: "حلل ملفات المشروع لتحديد التقنية وطرق التشغيل الصحيحة. ولا تفترض أن كل المشروعات تعمل بالطريقة نفسها."
     */
    fun detectTechRuntime(workspace: Workspace, files: List<WorkspaceFile>): DetectedTechRuntime {
        val filePaths = files.map { it.path }
        val packageJson = files.find { it.path == "package.json" || it.path.endsWith("/package.json") }

        if (packageJson != null) {
            val content = packageJson.content
            return when {
                content.contains("\"next\"") -> DetectedTechRuntime(
                    name = "Next.js (React Server & Client)",
                    command = "npm run dev",
                    buildCommand = "npm run build",
                    testCommand = "npm test",
                    configFile = packageJson.path,
                    defaultPort = 3000,
                    isBrowserPreviewSupported = true,
                    targetPlatform = "Web (Full-Stack)",
                    packageManager = "npm"
                )
                content.contains("\"vite\"") -> DetectedTechRuntime(
                    name = if (content.contains("\"vue\"")) "Vue 3 (Vite)" else "React (Vite)",
                    command = "npm run dev",
                    buildCommand = "npm run build",
                    testCommand = "npm test",
                    configFile = packageJson.path,
                    defaultPort = 5173,
                    isBrowserPreviewSupported = true,
                    targetPlatform = "Web (Single Page App)",
                    packageManager = "npm"
                )
                content.contains("\"react-native\"") -> DetectedTechRuntime(
                    name = "React Native (Metro Bundler)",
                    command = "npx react-native start",
                    buildCommand = "npx react-native build-android",
                    testCommand = "npm test",
                    configFile = packageJson.path,
                    defaultPort = 8081,
                    isBrowserPreviewSupported = false,
                    isNativeApp = true,
                    targetPlatform = "Mobile (Android / iOS)",
                    packageManager = "npm"
                )
                content.contains("\"express\"") || content.contains("\"fastify\"") -> DetectedTechRuntime(
                    name = "Node.js API Server",
                    command = "npm start",
                    buildCommand = "npm run build",
                    testCommand = "npm test",
                    configFile = packageJson.path,
                    defaultPort = 4000,
                    isBrowserPreviewSupported = true,
                    targetPlatform = "Backend API",
                    packageManager = "npm"
                )
                else -> DetectedTechRuntime(
                    name = "Node.js Web App",
                    command = "npm start",
                    buildCommand = "npm run build",
                    testCommand = "npm test",
                    configFile = packageJson.path,
                    defaultPort = 3000,
                    isBrowserPreviewSupported = true,
                    targetPlatform = "Web",
                    packageManager = "npm"
                )
            }
        }

        // Python Projects
        val reqsFile = files.find { it.path == "requirements.txt" }
        val pyMain = files.find { it.path == "main.py" || it.path == "app.py" }
        if (reqsFile != null || pyMain != null) {
            val isFastApi = reqsFile?.content?.contains("fastapi") == true || pyMain?.content?.contains("FastAPI") == true
            return DetectedTechRuntime(
                name = if (isFastApi) "Python (FastAPI + Uvicorn)" else "Python (Flask / App)",
                command = if (isFastApi) "uvicorn main:app --reload --port 8000" else "python ${pyMain?.path ?: "app.py"}",
                buildCommand = "pip install -r requirements.txt",
                testCommand = "pytest",
                configFile = reqsFile?.path ?: pyMain?.path ?: "main.py",
                defaultPort = 8000,
                isBrowserPreviewSupported = true,
                targetPlatform = "Web / API Server",
                packageManager = "pip"
            )
        }

        // Flutter Projects
        if (filePaths.any { it == "pubspec.yaml" || it.endsWith("/pubspec.yaml") }) {
            return DetectedTechRuntime(
                name = "Flutter Web & Mobile",
                command = "flutter run -d web-server --web-port 8080",
                buildCommand = "flutter build web",
                testCommand = "flutter test",
                configFile = "pubspec.yaml",
                defaultPort = 8080,
                isBrowserPreviewSupported = true,
                isNativeApp = true,
                targetPlatform = "Flutter Multi-Platform",
                packageManager = "flutter"
            )
        }

        // Android Native Projects
        if (filePaths.any { it.contains("build.gradle") || it.contains("AndroidManifest.xml") } || workspace.type == WorkspaceType.ANDROID) {
            return DetectedTechRuntime(
                name = "Android (Kotlin & Jetpack Compose)",
                command = "gradle :app:installDebug",
                buildCommand = "gradle :app:assembleDebug",
                testCommand = "gradle :app:testDebugUnitTest",
                configFile = "build.gradle.kts",
                defaultPort = 5554, // Default ADB emulator port
                isBrowserPreviewSupported = false,
                isNativeApp = true,
                targetPlatform = "Android Native (Emulator Required)",
                packageManager = "gradle"
            )
        }

        // Static HTML / Web Site
        if (filePaths.any { it.endsWith(".html") }) {
            return DetectedTechRuntime(
                name = "Static Web (HTML5 / Modern CSS)",
                command = "npx serve . -l 3000",
                buildCommand = "echo 'Static build complete'",
                testCommand = "npx htmlhint **/*.html",
                configFile = "index.html",
                defaultPort = 3000,
                isBrowserPreviewSupported = true,
                targetPlatform = "Web Browser",
                packageManager = "none"
            )
        }

        return DetectedTechRuntime(
            name = workspace.techStack.ifBlank { "Universal Sandbox" },
            command = "npm run dev",
            buildCommand = "npm run build",
            testCommand = "npm test",
            configFile = "package.json",
            defaultPort = 3000,
            isBrowserPreviewSupported = true,
            targetPlatform = workspace.type.name,
            packageManager = "npm"
        )
    }

    /**
     * Allocates or retrieves an isolated port for the workspace.
     */
    fun allocatePort(workspaceId: String, defaultPort: Int): Int {
        return allocatedPorts.getOrPut(workspaceId) {
            var candidate = defaultPort
            while (allocatedPorts.values.contains(candidate)) {
                candidate++
            }
            candidate
        }
    }

    fun releasePort(workspaceId: String) {
        allocatedPorts.remove(workspaceId)
    }

    /**
     * Handles workspace-only inspection and file operations.
     * Process execution fails closed until a verified isolated process runner is connected.
     */
    fun executeSandboxCommand(
        workspace: Workspace,
        files: List<WorkspaceFile>,
        commandLine: String
    ): SandboxExecutionOutput {
        val trimmed = commandLine.trim()
        val startTime = System.currentTimeMillis()
        val createdFiles = mutableListOf<WorkspaceFile>()
        val modifiedFiles = mutableListOf<WorkspaceFile>()

        // 1. Security Check: Sandbox isolation and prohibited dangerous host commands
        val lower = trimmed.lowercase()
        if (lower.contains("rm -rf /") || lower.contains("/etc/passwd") || lower.contains("../..") ||
            lower.contains("sudo ") || lower.contains(":(){ :|:& };:") || lower.contains("chmod -r 777 /")
        ) {
            val blockedResult = TerminalCommandResult(
                id = UUID.randomUUID().toString(),
                workspaceId = workspace.id,
                command = sanitizeOutput(trimmed),
                stdout = "",
                stderr = "SECURITY_VIOLATION: Command blocked. Operation attempts to escape Sandbox boundaries or execute privileged host actions.",
                exitCode = 126,
                durationMs = System.currentTimeMillis() - startTime,
                isSuccess = false,
                status = ProcessStatus.FAILED
            )
            return SandboxExecutionOutput(result = blockedResult)
        }

        val parts = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val rootCommand = parts.firstOrNull()?.lowercase() ?: ""

        var stdout = ""
        var stderr = ""
        var exitCode = 0

        when (rootCommand) {
            "help" -> {
                stdout = """
                    Vibe Studio workspace terminal
                    Workspace-only commands:
                      ls / dir          : List stored workspace files
                      cat <file>        : Read a stored workspace file
                      pwd               : Show the logical workspace path
                      grep <kw>         : Search stored workspace file contents
                      touch <file>      : Create an empty workspace file
                      clear             : Clear terminal output
                      help              : Show this help
                    Process commands (npm, pip, python, gradle, flutter, node, git, adb)
                    are blocked until a verified isolated process runner is connected.
                """.trimIndent()
            }

            "pwd" -> {
                stdout = "/workspace/${workspace.name.replace(" ", "_").lowercase()}"
            }

            "ls", "dir" -> {
                val nonFolders = files.filter { !it.isFolder }
                val folders = files.filter { it.isFolder }
                val listing = StringBuilder()
                folders.forEach { listing.append("drwxr-xr-x  [DIR]  ${it.path}\n") }
                nonFolders.forEach { listing.append("-rw-r--r--  ${it.content.length.toString().padStart(6)}B  ${it.path}\n") }
                stdout = listing.toString().trimEnd().ifEmpty { "Directory is empty" }
            }

            "cat" -> {
                val targetPath = parts.getOrNull(1)
                if (targetPath == null) {
                    stderr = "cat: missing file operand"
                    exitCode = 1
                } else {
                    val file = files.find { it.path == targetPath || it.path.endsWith("/$targetPath") }
                    if (file != null) {
                        stdout = file.content
                    } else {
                        stderr = "cat: $targetPath: No such file or directory in workspace"
                        exitCode = 1
                    }
                }
            }

            "grep" -> {
                val pattern = parts.getOrNull(1)
                if (pattern == null) {
                    stderr = "grep: missing pattern operand"
                    exitCode = 1
                } else {
                    val matching = files.filter { !it.isFolder && it.content.contains(pattern, ignoreCase = true) }
                    stdout = matching.joinToString("\n") { file ->
                        val matchingLines = file.content.lines().filter { it.contains(pattern, ignoreCase = true) }
                        "${file.path}: ${matchingLines.firstOrNull()?.trim()}"
                    }.ifEmpty { "No matches found." }
                }
            }

            "npm", "pip", "python", "python3", "gradle", "./gradlew", "flutter", "npx", "node", "git", "adb" -> {
                stderr = "PROCESS_EXECUTION_UNAVAILABLE: No verified isolated process runner is connected. The command was not executed."
                exitCode = 126
            }

            "ports", "ps", "kill", "env" -> {
                stderr = "PROCESS_INSPECTION_UNAVAILABLE: No real process or environment backend is connected. No process state was changed."
                exitCode = 126
            }

            "clear" -> {
                stdout = ""
            }

            "touch" -> {
                val targetPath = parts.getOrNull(1)
                if (targetPath == null) {
                    stderr = "touch: missing file operand"
                    exitCode = 1
                } else {
                    val existing = files.find { it.path == targetPath }
                    if (existing == null) {
                        val newFile = WorkspaceFile(
                            id = UUID.randomUUID().toString(),
                            workspaceId = workspace.id,
                            name = targetPath.substringAfterLast('/'),
                            path = targetPath,
                            content = "",
                            extension = targetPath.substringAfterLast('.', "")
                        )
                        createdFiles.add(newFile)
                        stdout = "Created file $targetPath"
                    } else {
                        stdout = "Updated timestamp for $targetPath"
                    }
                }
            }

            else -> {
                stderr = "COMMAND_UNSUPPORTED: Only workspace inspection and file operations are available without a verified process runner."
                exitCode = 127
            }
        }

        val duration = System.currentTimeMillis() - startTime
        val result = TerminalCommandResult(
            id = UUID.randomUUID().toString(),
            workspaceId = workspace.id,
            command = sanitizeOutput(trimmed),
            stdout = sanitizeOutput(stdout),
            stderr = sanitizeOutput(stderr),
            exitCode = exitCode,
            durationMs = duration,
            isSuccess = exitCode == 0,
            status = if (exitCode == 0) ProcessStatus.COMPLETED else ProcessStatus.FAILED
        )
        return SandboxExecutionOutput(
            result = result,
            createdFiles = createdFiles,
            modifiedFiles = modifiedFiles
        )
    }

    /**
     * Sanitizes output to ensure no API keys or credentials leak into logs or terminals.
     * Complies with requirement #7: "لا تعرض مفاتيح API داخل Terminal أو سجلات التنفيذ."
     */
    fun sanitizeOutput(raw: String): String {
        return raw
            .replace(Regex("AIza[0-9A-Za-z-_]{35}"), "[REDACTED_GEMINI_KEY]")
            .replace(Regex("ghp_[0-9A-Za-z]{36}"), "[REDACTED_GITHUB_TOKEN]")
            .replace(Regex("sk-[0-9A-Za-z]{32,}"), "[REDACTED_API_KEY]")
            .replace(Regex("key-[0-9a-f]{32}"), "[REDACTED_KEY]")
    }

    /**
     * Produces an isolated static HTML preview when an index.html entry point exists.
     * Framework execution requires a separate verified process runner.
     */
    fun generatePreviewHtml(workspace: Workspace, files: List<WorkspaceFile>): String {
        val indexHtml = files.find { it.path == "index.html" || it.path.endsWith("/index.html") }
        val cssFiles = files.filter { it.extension in listOf("css", "scss") }

        val combinedCss = cssFiles.joinToString("\n") { it.content }

        if (indexHtml != null) {
            var html = indexHtml.content
            if (combinedCss.isNotBlank() && !html.contains("<style>")) {
                html = html.replace("</head>", "<style>\n$combinedCss\n</style>\n</head>")
            }
            return PreviewHtmlSecurityPolicy.injectContentSecurityPolicy(injectConsoleBridge(html))
        }

        // Do not display a fabricated application when there is no runnable HTML entry point.
        val safeWorkspaceName = escapeHtml(workspace.name.ifBlank { "Workspace" })
        val safeDescription = escapeHtml(
            workspace.description.ifBlank {
                "No index.html entry point is available. Framework previews require a verified execution backend."
            }
        )
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Preview unavailable</title>
                <style>
                    * { box-sizing: border-box; }
                    body { margin: 0; min-height: 100vh; display: grid; place-items: center; padding: 24px; background: #0f172a; color: #e2e8f0; font-family: system-ui, sans-serif; }
                    main { width: min(100%, 560px); padding: 28px; border: 1px solid #334155; border-radius: 18px; background: #172033; }
                    h1 { margin: 0 0 12px; font-size: 22px; }
                    p { line-height: 1.6; color: #cbd5e1; }
                    .label { color: #67e8f9; font-size: 12px; font-weight: 700; letter-spacing: .08em; }
                </style>
            </head>
            <body>
                <main>
                    <div class="label">WORKSPACE PREVIEW</div>
                    <h1>Preview unavailable</h1>
                    <p>$safeDescription</p>
                    <p>Workspace: $safeWorkspaceName</p>
                    <p>No build, process, emulator or live server has been started.</p>
                </main>
            </body>
            </html>
        """.trimIndent()
        return PreviewHtmlSecurityPolicy.injectContentSecurityPolicy(html)
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun injectConsoleBridge(html: String): String {
        val bridgeScript = """
            <script>
                (function() {
                    const originalLog = console.log;
                    const originalError = console.error;
                    const originalWarn = console.warn;
                    const originalInfo = console.info;

                    console.log = function(...args) {
                        originalLog.apply(console, args);
                    };
                    console.error = function(...args) {
                        originalError.apply(console, args);
                    };
                    console.warn = function(...args) {
                        originalWarn.apply(console, args);
                    };
                    console.info = function(...args) {
                        originalInfo.apply(console, args);
                    };
                })();
            </script>
        """.trimIndent()

        return if (html.contains("<head>")) {
            html.replace("<head>", "<head>\n$bridgeScript")
        } else {
            bridgeScript + html
        }
    }
}

data class SandboxExecutionOutput(
    val result: TerminalCommandResult,
    val createdFiles: List<WorkspaceFile> = emptyList(),
    val modifiedFiles: List<WorkspaceFile> = emptyList()
)
