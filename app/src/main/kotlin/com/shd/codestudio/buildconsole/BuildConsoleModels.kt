package com.shd.codestudio.buildconsole

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BuildConsoleTab(val title: String) {
    BUILD_OUTPUT("Build output"),
    APP_LOGS("App Logs"),
    TERMINAL("Terminal"),
    DIAGNOSTICS("Diagnostics")
}

enum class BuildStatus {
    IDLE,
    BUILDING,
    SUCCESS,
    FAILED
}

enum class LogSeverity {
    INFO,
    TASK,
    SUCCESS,
    WARNING,
    ERROR,
    RUNNER
}

data class BuildLogLine(
    val id: Long,
    val timestamp: String,
    val text: String,
    val severity: LogSeverity = classifyLogSeverity(text)
)

data class DiagnosticIssue(
    val filePath: String,
    val line: Int,
    val message: String,
    val isError: Boolean
)

data class BuildConsoleState(
    val status: BuildStatus = BuildStatus.IDLE,
    val title: String = "Build Console Ready",
    val subtitle: String = "Swipe up or tap for build output, logs & more",
    val durationText: String = "",
    val buildLogs: List<BuildLogLine> = emptyList(),
    val appLogs: List<BuildLogLine> = emptyList(),
    val terminalLines: List<BuildLogLine> = listOf(
        BuildLogLine(
            id = 1L,
            timestamp = currentLogTimestamp(),
            text = "CodeStudio Shell v2.4 — Type 'help' for available commands.",
            severity = LogSeverity.INFO
        )
    ),
    val diagnostics: List<DiagnosticIssue> = emptyList(),
    val actionsUrl: String = "",
    val completedRunId: Long? = null,
    val gitOwner: String = "",
    val gitRepo: String = "",
    val isDownloadingApk: Boolean = false
)

fun currentLogTimestamp(): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

fun formatBuildDuration(elapsedMs: Long): String {
    if (elapsedMs < 1000L) return "${elapsedMs}ms"
    val totalSec = elapsedMs / 1000L
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return if (mins > 0L) "${mins}m ${secs}s" else "${secs}s"
}

fun classifyLogSeverity(text: String): LogSeverity {
    return when {
        text.contains("BUILD SUCCESSFUL", ignoreCase = true) ||
            text.contains("CLOUD BUILD SUCCESSFUL", ignoreCase = true) ||
            text.contains("SUCCESS:", ignoreCase = true) -> LogSeverity.SUCCESS
        text.contains("ERROR", ignoreCase = true) ||
            text.contains("FAILED", ignoreCase = true) ||
            text.contains("Exception", ignoreCase = true) -> LogSeverity.ERROR
        text.contains("WARNING", ignoreCase = true) ||
            text.contains("WARN", ignoreCase = true) -> LogSeverity.WARNING
        text.contains("GitHub Runner", ignoreCase = true) ||
            text.contains("GitHub", ignoreCase = true) -> LogSeverity.RUNNER
        text.trimStart().startsWith("> Task") ||
            text.contains("[1/6]") || text.contains("[2/6]") ||
            text.contains("[3/6]") || text.contains("[4/6]") ||
            text.contains("[5/6]") || text.contains("[6/6]") -> LogSeverity.TASK
        else -> LogSeverity.INFO
    }
}