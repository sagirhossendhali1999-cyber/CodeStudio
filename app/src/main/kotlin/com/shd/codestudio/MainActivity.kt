package com.shd.codestudio

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.shd.codestudio.editorscreen.EditorScreen
import com.shd.codestudio.homescreen.HomeScreen
import com.shd.codestudio.homescreen.loadProjects
import com.shd.codestudio.models.Project
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        private const val CRASH_PREFS = "CodeStudioCrashPrefs"
        private const val KEY_LAST_CRASH = "last_crash"
        private const val KEY_LAST_SUMMARY = "last_crash_summary"
        private var handlerInstalled = false

        fun installCrashHandler(appContext: Context) {
            if (handlerInstalled) return
            handlerInstalled = true

            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    val sw = StringWriter()
                    throwable.printStackTrace(PrintWriter(sw))
                    val stackTrace = sw.toString()

                    val traceLines = stackTrace.lines()
                    val errorTitle = traceLines.firstOrNull()?.take(200) ?: (throwable.message ?: "Unknown Crash")
                    val appFaultLine = traceLines.firstOrNull { it.contains("com.shd.codestudio") }?.trim()
                        ?: "Location: System / Library"

                    val timeStr = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()).format(Date())
                    val versionName = try {
                        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "1.0"
                    } catch (_: Exception) { "1.0" }

                    val fullReport = buildString {
                        appendLine("=== CodeStudio Crash Report ===")
                        appendLine("Time: $timeStr")
                        appendLine("App Version: $versionName")
                        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        appendLine("Thread: ${thread.name}")
                        appendLine("--------------------------------")
                        appendLine(stackTrace)
                    }

                    val summaryText = "$errorTitle\n$appFaultLine"

                    val prefs = appContext.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString(KEY_LAST_CRASH, fullReport)
                        .putString(KEY_LAST_SUMMARY, summaryText)
                        .commit()

                    val logDir = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "CrashLogs")
                    if (!logDir.exists()) logDir.mkdirs()
                    val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    File(logDir, "crash_$fileStamp.txt").writeText(fullReport)
                } catch (_: Exception) { }

                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val prefs = getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)

        val previousCrashLog = if (isDebuggable) prefs.getString(KEY_LAST_CRASH, null) else null
        val previousCrashSummary = if (isDebuggable) prefs.getString(KEY_LAST_SUMMARY, null) else null

        if (isDebuggable) {
            installCrashHandler(applicationContext)
        }

        cleanupStaleTempFiles()
        handleIncomingIntent(intent)

        actionBar?.hide()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.parseColor("#0A0A0A")
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.parseColor("#0A0A0A")
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            CodeStudioTheme {
                var activeProjectName by rememberSaveable { mutableStateOf<String?>(null) }
                var activeProject by remember {
                    mutableStateOf<Project?>(
                        activeProjectName?.let { name ->
                            loadProjects(this@MainActivity).find { it.name == name }
                        }
                    )
                }
                var crashDialogText by remember { mutableStateOf(previousCrashLog) }
                var crashSummaryText by remember { mutableStateOf(previousCrashSummary) }
                val clipboard = LocalClipboardManager.current

                fun clearSavedCrash() {
                    prefs.edit().remove(KEY_LAST_CRASH).remove(KEY_LAST_SUMMARY).apply()
                    crashDialogText = null
                    crashSummaryText = null
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBackground)
                ) {
                    if (activeProject == null) {
                        HomeScreen(
                            onOpenProject = { project ->
                                Log.d("CodeStudioDebug", "Opening project: ${project.name}")
                                activeProject = project
                                activeProjectName = project.name
                            }
                        )
                    } else {
                        EditorScreen(
                            project = activeProject!!,
                            onBack = {
                                activeProject = null
                                activeProjectName = null
                            }
                        )
                    }
                }

                if (crashDialogText != null) {
                    AlertDialog(
                        onDismissRequest = { clearSavedCrash() },
                        containerColor = Color(0xFF161616),
                        title = {
                            Text(
                                text = "App Crash Detected",
                                color = Color(0xFFFF5252),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        },
                        text = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (!crashSummaryText.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFFF5252).copy(alpha = 0.12f))
                                            .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = crashSummaryText ?: "",
                                            color = Color(0xFFFF8A80),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 280.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0B0B0B))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                        .verticalScroll(rememberScrollState())
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = crashDialogText ?: "",
                                        color = Color(0xFFE5E5E5),
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "CodeStudio Crash Log")
                                            putExtra(Intent.EXTRA_TEXT, crashDialogText ?: "")
                                        }
                                        startActivity(Intent.createChooser(shareIntent, "Share Crash Log"))
                                    }
                                ) {
                                    Text("Share", color = Color.White, fontWeight = FontWeight.Medium)
                                }
                                TextButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(crashDialogText ?: ""))
                                        Toast.makeText(this@MainActivity, "Crash log copied!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("Copy Error", color = NeonLime, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { clearSavedCrash() }) {
                                Text("Close", color = TextSecondary)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "codestudio" && data.host == "github-callback") {
            Log.d("CodeStudioDebug", "GitHub callback received: $data")
        }
    }

    private fun cleanupStaleTempFiles() {
        Thread {
            try {
                val tmpDir = File(applicationContext.filesDir, "CodeStudioEngine/tmp")
                if (tmpDir.exists()) {
                    val cutoff = System.currentTimeMillis() - 6L * 60L * 60L * 1000L
                    tmpDir.listFiles()?.forEach { f ->
                        if (f.lastModified() < cutoff) {
                            f.deleteRecursively()
                        }
                    }
                }
            } catch (_: Exception) { }
        }.start()
    }
}