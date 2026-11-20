package com.shd.codestudio.editorscreen

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shd.codestudio.DarkBackground
import com.shd.codestudio.NeonLime
import com.shd.codestudio.TextSecondary
import com.shd.codestudio.git.CloneRepositoryScreen
import com.shd.codestudio.git.GitHubRepoItem
import com.shd.codestudio.git.GitManager
import com.shd.codestudio.git.GitStorage
import com.shd.codestudio.models.Project
import com.shd.codestudio.nativeengine.ApkInstaller
import com.shd.codestudio.nativeengine.DeclarativeProjectParser
import com.shd.codestudio.nativeengine.EngineBuildVariant
import com.shd.codestudio.nativeengine.EngineManagerDialog
import com.shd.codestudio.nativeengine.NativeBuildOrchestrator
import com.shd.codestudio.nativeengine.NativeBuildRequest
import com.shd.codestudio.nativeengine.SdkEngineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun LivePreviewPage(
    project: Project,
    fileCount: Int,
    onBackToCode: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var isPhone by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var isPullRefreshing by remember { mutableStateOf(false) }

    var appName by remember { mutableStateOf(project.name) }
    var packageName by remember { mutableStateOf("com.example.${project.name.lowercase().replace(" ", "")}") }
    var projectCompileSdk by remember { mutableIntStateOf(36) }

    var liveUiNode by remember { mutableStateOf<UiNode?>(null) }
    var isLoadingUi by remember { mutableStateOf(true) }

    // বিল্ড টার্মিনাল ও রান স্টেট
    var showBuildTerminal by remember { mutableStateOf(false) }
    var buildTitle by remember { mutableStateOf("") }
    var buildLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var isBuilding by remember { mutableStateOf(false) }
    var buildSuccess by remember { mutableStateOf<Boolean?>(null) }

    // Zero-Setup Native Engine স্টেট
    var localBuiltApkPath by remember { mutableStateOf("") }
    var isLocalBuildMode by remember { mutableStateOf(false) }
    val cancelLocalBuildFlag = remember { AtomicBoolean(false) }
    var showNativeBuildOptions by remember { mutableStateOf(false) }
    var showEngineManager by remember { mutableStateOf(false) }
    var selectedNativeVariant by remember { mutableStateOf(EngineBuildVariant.DEBUG) }
    var cleanFirstBuild by remember { mutableStateOf(false) }

    // GitHub Cloud CI/CD ট্র্যাকিং ও APK ডাউনলোড স্টেট
    var activeGitOwner by remember { mutableStateOf("") }
    var activeGitRepo by remember { mutableStateOf("") }
    var activeActionsUrl by remember { mutableStateOf("") }
    var completedRunId by remember { mutableStateOf<Long?>(null) }
    var isDownloadingApk by remember { mutableStateOf(false) }
    var buildPollingJob by remember { mutableStateOf<Job?>(null) }

    // GitHub সেটআপ ও কমিট ডায়ালগ স্টেট
    var showLoginRequiredDialog by remember { mutableStateOf(false) }
    var showInAppCloneAuthScreen by remember { mutableStateOf(false) }
    var showFirstTimeRepoSetupDialog by remember { mutableStateOf(false) }
    var showCommitMessageDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    LaunchedEffect(project.name, refreshTrigger) {
        isLoadingUi = true
        delay(60)
        val (resolvedName, resolvedPkg, parsedSdk) = withContext(Dispatchers.IO) {
            val name = getAppNameFromProject(context, project.name)
            val pkg = getPackageNameFromProject(context, project.name)
            val sdk = runCatching {
                DeclarativeProjectParser.parseProject(context, project.name).appModule.compileSdk
            }.getOrDefault(36)
            Triple(name, pkg, sdk)
        }
        appName = resolvedName
        packageName = resolvedPkg
        projectCompileSdk = parsedSdk

        val parsedNode = ProjectUiInterpreter.parseProjectUi(context, project.name)
        liveUiNode = parsedNode
        isLoadingUi = false
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isPullRefreshing,
        onRefresh = {
            isPullRefreshing = true
            refreshTrigger++
            scope.launch {
                delay(380)
                isPullRefreshing = false
                Toast.makeText(context, "Preview reloaded!", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // ================= ১. Zero-Setup Auto-Pilot Native Build Engine =================
    fun runNativeInProcessBuildEngine(
        variant: EngineBuildVariant = selectedNativeVariant,
        cleanFirst: Boolean = cleanFirstBuild
    ) {
        buildPollingJob?.cancel()
        cancelLocalBuildFlag.set(false)
        activeActionsUrl = ""
        completedRunId = null
        localBuiltApkPath = ""
        isLocalBuildMode = true

        buildTitle = "Native Build (${variant.label}): ${project.name}"
        val initialLogs = listOf(
            "CodeStudio Zero-Setup Auto-Pilot Engine",
            "Target: ${project.name} (${variant.label}${if (cleanFirst) " • Clean" else ""})",
            "Launching isolated compiler worker..."
        )
        buildLogs = initialLogs
        showBuildTerminal = true
        isBuilding = true
        buildSuccess = null

        buildPollingJob = scope.launch(Dispatchers.IO) {
            val logList = initialLogs.toMutableList()
            val logLock = Any()

            fun addLog(msg: String) {
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val snapshot = synchronized(logLock) {
                    logList.add("[$time] $msg")
                    if (logList.size > 1500) {
                        logList.removeAt(0)
                    }
                    logList.toList()
                }
                scope.launch(Dispatchers.Main) {
                    buildLogs = snapshot
                }
            }

            val keystoreCfg = if (!variant.isDebug) SdkEngineManager.loadKeystoreConfig(context) else null

            val result = NativeBuildOrchestrator.runBuild(
                context = context,
                request = NativeBuildRequest(
                    projectName = project.name,
                    variant = variant,
                    cleanFirst = cleanFirst,
                    customKeystore = keystoreCfg
                ),
                useIsolatedProcess = true,
                isCancelled = { cancelLocalBuildFlag.get() },
                onLog = { line -> addLog(line) }
            )

            if (result.success && result.outputApkPath.isNotEmpty()) {
                addLog("APK Ready: ${result.outputApkPath}")
            }

            withContext(Dispatchers.Main) {
                localBuiltApkPath = result.outputApkPath
                isBuilding = false
                buildSuccess = result.success
            }
        }
    }

    // ================= ২. ক্লাউড CI/CD বিল্ড পাইপলাইন =================
    fun runFullGitHubPushAndBuild(
        tokenOverride: String? = null,
        customUrlOverride: String? = null,
        branchOverride: String? = null,
        commitMsgOverride: String? = null,
        isPrivateRepo: Boolean = false,
        injectWorkflow: Boolean = true
    ) {
        val creds = GitStorage.loadCredentials(context)
        val tokenToUse = (tokenOverride ?: creds.token).trim()
        val (savedUrl, savedBranch) = GitStorage.loadRepoRemote(context, project.name)
        val targetUrl = (customUrlOverride ?: savedUrl).trim()
        val targetBranch = (branchOverride ?: savedBranch).ifBlank { "main" }
        val commitMsg = (commitMsgOverride ?: "Update '${project.name}' from CodeStudio").trim()

        buildPollingJob?.cancel()
        isLocalBuildMode = false
        localBuiltApkPath = ""
        activeGitOwner = ""
        activeGitRepo = ""
        activeActionsUrl = ""
        completedRunId = null

        buildTitle = "GitHub CI/CD: Cloud Build"
        val initialLogs = listOf(
            "CodeStudio Cloud Actions Pipeline",
            "Project: ${project.name}",
            "Syncing repository & launching remote build..."
        )
        buildLogs = initialLogs
        showBuildTerminal = true
        isBuilding = true
        buildSuccess = null

        buildPollingJob = scope.launch(Dispatchers.IO) {
            val logList = initialLogs.toMutableList()
            val logLock = Any()

            fun addLog(msg: String) {
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val snapshot = synchronized(logLock) {
                    logList.add("[$time] $msg")
                    logList.toList()
                }
                scope.launch(Dispatchers.Main) {
                    buildLogs = snapshot
                }
            }

            val pushResult = GitManager.autoPushAndBuild(
                context = context,
                projectName = project.name,
                token = tokenToUse,
                customRepoUrl = targetUrl,
                preferredBranch = targetBranch,
                commitMessage = commitMsg,
                isPrivateRepo = isPrivateRepo,
                injectWorkflowIfMissing = injectWorkflow,
                allowAutoCreate = false,
                onLog = { msg -> addLog(msg) }
            )

            if (!pushResult.success) {
                addLog("ERROR: ${pushResult.message}")
                withContext(Dispatchers.Main) {
                    isBuilding = false
                    buildSuccess = false
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                activeGitOwner = pushResult.owner
                activeGitRepo = pushResult.repoName
                activeActionsUrl = "${pushResult.repoHtmlUrl}/actions"
            }

            addLog("SUCCESS: Repository synced (${pushResult.owner}/${pushResult.repoName})")
            addLog("Monitoring GitHub Actions cloud runner...")

            var lastStatus = ""
            var attempts = 0
            while (isActive && attempts < 80) {
                delay(4000)
                attempts++
                val runRes = GitManager.getLatestWorkflowRun(
                    token = tokenToUse,
                    owner = pushResult.owner,
                    repoName = pushResult.repoName,
                    branch = pushResult.branch
                )
                val runInfo = runRes.getOrNull()
                if (runInfo != null) {
                    withContext(Dispatchers.Main) {
                        activeActionsUrl = runInfo.htmlUrl
                    }
                    val statusTag = "${runInfo.status}:${runInfo.conclusion}"
                    if (statusTag != lastStatus) {
                        lastStatus = statusTag
                        when (runInfo.status) {
                            "queued" -> addLog("GitHub Runner: Build queued, waiting for VM...")
                            "in_progress" -> addLog("GitHub Runner: Building APK in cloud...")
                            "completed" -> {
                                if (runInfo.conclusion == "success") {
                                    addLog("CLOUD BUILD SUCCESSFUL! APK is ready to download.")
                                    withContext(Dispatchers.Main) {
                                        completedRunId = runInfo.id
                                        isBuilding = false
                                        buildSuccess = true
                                    }
                                } else {
                                    addLog("FAILED: Cloud build finished with status '${runInfo.conclusion ?: "error"}'.")
                                    withContext(Dispatchers.Main) {
                                        isBuilding = false
                                        buildSuccess = false
                                    }
                                }
                                return@launch
                            }
                        }
                    }
                }
            }

            val stillRunning = withContext(Dispatchers.Main) { isBuilding }
            if (stillRunning) {
                addLog("Build is running in GitHub Cloud. Tap 'Open Actions' to view progress.")
                withContext(Dispatchers.Main) {
                    isBuilding = false
                    buildSuccess = true
                }
            }
        }
    }

    fun handleBuildWithGitHubClick() {
        val creds = GitStorage.loadCredentials(context)
        if (creds.username.isBlank()) {
            showLoginRequiredDialog = true
            return
        }
        if (creds.token.isBlank()) {
            showFirstTimeRepoSetupDialog = true
            return
        }
        showCommitMessageDialog = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(colors = listOf(Color(0xFF262626), Color(0xFF141414))))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
    ) {
        // ================= ৩. প্রিভিউ প্রো হেডার =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.3f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(NeonLime)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = "Live Interactive Preview",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Phone / Tablet সুইচার
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF22242E))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isPhone) NeonLime else Color.Transparent)
                        .clickable { isPhone = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Phone", color = if (isPhone) Color.Black else Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (!isPhone) NeonLime else Color.Transparent)
                        .clickable { isPhone = false }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Tablet", color = if (!isPhone) Color.Black else Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Fullscreen Run Toggle
            IconButton(
                onClick = { isFullscreen = !isFullscreen },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen Run Mode",
                    tint = NeonLime,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ================= ৪. জিরো-সেটআপ বিল্ড বাটন (১-ক্লিকে অন-ডিভাইস রান) =================
        if (!isFullscreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ১-ক্লিক মেইন রান বাটন (কোনো সেটআপ ছাড়া সরাসরি APK তৈরি করে ইনস্টল করবে)
                Button(
                    onClick = {
                        runNativeInProcessBuildEngine(selectedNativeVariant, cleanFirst = false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.weight(1.35f).height(44.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Run / Build APK", fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1)
                }

                // ক্লাউড বিল্ড বাটন
                Button(
                    onClick = { handleBuildWithGitHubClick() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24292E), contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Outlined.Cloud, contentDescription = null, tint = Color(0xFF58A6FF), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Cloud Build", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                }

                // বিল্ড ভ্যারিয়েন্ট ও ইঞ্জিন অপশন
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1F2129))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .clickable { showNativeBuildOptions = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = "Build Options",
                        tint = NeonLime,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // ================= ৫. প্রিভিউ ক্যানভাস (আসল ফোন ফ্রেম) =================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pullRefresh(pullRefreshState),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isFullscreen) Modifier
                        else Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (isFullscreen) Modifier.fillMaxSize()
                            else Modifier.size(
                                width = if (isPhone) 290.dp else 360.dp,
                                height = if (isPhone) 510.dp else 400.dp
                            )
                        )
                        .background(Color.Black, if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(32.dp))
                        .then(if (isFullscreen) Modifier else Modifier.border(3.5.dp, Color(0xFF383838), RoundedCornerShape(32.dp)))
                        .padding(if (isFullscreen) 0.dp else 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(24.dp))
                            .background(Color(0xFF0E0F14))
                    ) {
                        // ১) ফোনের স্ট্যাটাস বার (Punch-Hole ক্যামেরা সহ)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .background(Color(0xFF121318))
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val timeStr = SimpleDateFormat("hh:mm", Locale.getDefault()).format(Date())
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(timeStr, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.SignalCellularAlt, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(11.dp))
                                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(11.dp))
                                    Icon(Icons.Filled.BatteryFull, contentDescription = null, tint = NeonLime, modifier = Modifier.size(12.dp))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(11.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black)
                            )
                        }

                        // ২) লাইভ ইন্টারেক্টিভ অ্যাপ
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            if (isLoadingUi) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = NeonLime, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                                }
                            } else if (liveUiNode != null) {
                                key(refreshTrigger) {
                                    InteractiveAppHost(
                                        rootNode = liveUiNode!!,
                                        modifier = Modifier.fillMaxSize(),
                                        externalSelectedScreen = null
                                    )
                                }
                            }
                        }

                        // ৩) অ্যান্ড্রয়েড জেসচার নেভিগেশন বার
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                                .background(Color(0xFF121318)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(90.dp)
                                    .height(3.5.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.35f))
                            )
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = isPullRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                contentColor = NeonLime,
                backgroundColor = Color(0xFF1E1E1E)
            )
        }
    }
    // ================= ৬. Build Options & Keystore Dialog =================
    if (showNativeBuildOptions) {
        val activeKeystore = remember(showNativeBuildOptions) { SdkEngineManager.loadKeystoreConfig(context) }
        val kotlincInstalled = remember(showNativeBuildOptions) { SdkEngineManager.isKotlincInstalled(context) }

        Dialog(onDismissRequest = { showNativeBuildOptions = false }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF14151B),
                border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(0.93f)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 18.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 34.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NeonLime.copy(alpha = 0.15f))
                                    .border(0.8.dp, NeonLime.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Build, contentDescription = null, tint = NeonLime, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Build Options", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("Target API $projectCompileSdk • Zero-Setup Auto-Pilot", color = TextSecondary, fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Zero-Setup Auto-Pilot Engine Banner
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1A1D24))
                                .border(0.8.dp, Color(0xFF58A6FF).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .clickable {
                                    showNativeBuildOptions = false
                                    showEngineManager = true
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = null, tint = Color(0xFF58A6FF), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = if (kotlincInstalled) {
                                    "Engine Ready (SDK + Kotlin/Compose). Tap to manage."
                                } else {
                                    "Auto-Pilot enabled (auto-downloads SDK/Kotlin if needed). Tap to manage."
                                },
                                color = Color(0xFF8EC5FF),
                                fontSize = 10.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("BUILD VARIANT", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                EngineBuildVariant.DEBUG to "Debug (Fast)",
                                EngineBuildVariant.RELEASE to "Release (R8)"
                            ).forEach { (variant, label) ->
                                val active = selectedNativeVariant == variant
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(if (active) NeonLime.copy(alpha = 0.18f) else Color(0xFF1E2028))
                                        .border(
                                            1.dp,
                                            if (active) NeonLime else Color.White.copy(alpha = 0.1f),
                                            RoundedCornerShape(11.dp)
                                        )
                                        .clickable { selectedNativeVariant = variant }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (active) NeonLime else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (selectedNativeVariant == EngineBuildVariant.RELEASE) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1A1D24))
                                    .border(
                                        0.8.dp,
                                        if (activeKeystore != null) NeonLime.copy(alpha = 0.45f) else Color(0xFFFFB020).copy(alpha = 0.45f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        showNativeBuildOptions = false
                                        showEngineManager = true
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = if (activeKeystore != null) NeonLime else Color(0xFFFFB020),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = if (activeKeystore != null) {
                                        "Release Keystore: ${activeKeystore.keystoreFile.name}"
                                    } else {
                                        "No custom keystore set (uses Debug key). Tap to configure."
                                    },
                                    color = if (activeKeystore != null) NeonLime else Color(0xFFFFCC66),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { cleanFirstBuild = !cleanFirstBuild }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = cleanFirstBuild,
                                onCheckedChange = { cleanFirstBuild = it },
                                colors = CheckboxDefaults.colors(checkedColor = NeonLime, checkmarkColor = Color.Black)
                            )
                            Text("Clean build (ignore incremental task cache)", color = Color.LightGray, fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showNativeBuildOptions = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF23242C), contentColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            Button(
                                onClick = {
                                    showNativeBuildOptions = false
                                    runNativeInProcessBuildEngine(selectedNativeVariant, cleanFirstBuild)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.4f).height(44.dp)
                            ) {
                                Text("Run Build", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF23242C))
                            .clickable { showNativeBuildOptions = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }

    if (showEngineManager) {
        EngineManagerDialog(
            recommendedApi = projectCompileSdk,
            onDismiss = { showEngineManager = false },
            onReadyToBuild = {
                runNativeInProcessBuildEngine(selectedNativeVariant, cleanFirstBuild)
            }
        )
    }

    // ================= ৭. GitHub Login Dialog =================
    if (showLoginRequiredDialog) {
        Dialog(onDismissRequest = { showLoginRequiredDialog = false }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF16161B),
                border = BorderStroke(1.dp, Color(0xFF58A6FF).copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF23242C))
                            .clickable { showLoginRequiredDialog = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray, modifier = Modifier.size(15.dp))
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF58A6FF).copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Key, contentDescription = null, tint = Color(0xFF58A6FF), modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "GitHub Account Required",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "To use Cloud Build, please sign in with your GitHub account.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showLoginRequiredDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26262E), contentColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("No", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Button(
                                onClick = {
                                    showLoginRequiredDialog = false
                                    showInAppCloneAuthScreen = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("Yes", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInAppCloneAuthScreen) {
        Dialog(
            onDismissRequest = { showInAppCloneAuthScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = DarkBackground
            ) {
                CloneRepositoryScreen(
                    onBack = {
                        showInAppCloneAuthScreen = false
                        val updatedCreds = GitStorage.loadCredentials(context)
                        if (updatedCreds.username.isNotBlank()) {
                            handleBuildWithGitHubClick()
                        }
                    },
                    onStartClone = { repoUrl, _, branch, _ ->
                        GitStorage.saveRepoRemote(context, project.name, repoUrl, branch)
                        showInAppCloneAuthScreen = false
                        Toast.makeText(context, "Repository linked!", Toast.LENGTH_SHORT).show()
                        handleBuildWithGitHubClick()
                    }
                )
            }
        }
    }

    // ================= ৮. Setup GitHub Repository ডায়ালগ =================
    if (showFirstTimeRepoSetupDialog) {
        val creds = GitStorage.loadCredentials(context)
        val (savedUrl, savedBranch) = GitStorage.loadRepoRemote(context, project.name)
        val sanitizedDefaultName = GitManager.sanitizeRepoName(project.name)

        var setupTab by remember { mutableIntStateOf(0) }
        var token by remember { mutableStateOf(creds.token) }
        var showToken by remember { mutableStateOf(false) }

        var newRepoName by remember { mutableStateOf(sanitizedDefaultName) }
        var newRepoDesc by remember { mutableStateOf("Android project built with CodeStudio IDE") }
        var isPrivateRepo by remember { mutableStateOf(false) }
        var isCreatingRepoNow by remember { mutableStateOf(false) }
        var createdRepoItem by remember { mutableStateOf<GitHubRepoItem?>(null) }
        var createRepoError by remember { mutableStateOf("") }
        var showUseExistingHelper by remember { mutableStateOf(false) }

        var repoUrl by remember { mutableStateOf("") }
        var myExistingRepos by remember { mutableStateOf<List<GitHubRepoItem>>(emptyList()) }
        var isLoadingExistingRepos by remember { mutableStateOf(false) }
        var existingRepoError by remember { mutableStateOf("") }
        var isValidatingSelection by remember { mutableStateOf(false) }

        var branch by remember { mutableStateOf(savedBranch.ifEmpty { "main" }) }
        var commitMsg by remember { mutableStateOf("Initial push & build '${project.name}' from CodeStudio") }
        var injectWorkflow by remember { mutableStateOf(true) }

        fun loadExistingRepos(autoMatchSaved: Boolean = false) {
            val t = token.trim().ifEmpty { creds.token }
            isLoadingExistingRepos = true
            scope.launch(Dispatchers.IO) {
                val res = GitManager.fetchUserRepositories(t, creds.username)
                withContext(Dispatchers.Main) {
                    isLoadingExistingRepos = false
                    res.onSuccess { list ->
                        myExistingRepos = list
                        if (autoMatchSaved && savedUrl.isNotBlank() && repoUrl.isBlank()) {
                            val matched = list.firstOrNull {
                                it.htmlUrl.equals(savedUrl, ignoreCase = true) ||
                                    it.cloneUrl.equals(savedUrl, ignoreCase = true)
                            }
                            if (matched != null) {
                                repoUrl = matched.htmlUrl
                                branch = matched.defaultBranch
                                setupTab = 1
                            }
                        }
                    }.onFailure { err ->
                        Toast.makeText(context, err.message ?: "Failed to load repositories", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            if (creds.username.isNotBlank() || creds.token.isNotBlank()) {
                loadExistingRepos(autoMatchSaved = true)
            }
        }

        Dialog(
            onDismissRequest = { if (!isCreatingRepoNow && !isValidatingSelection) showFirstTimeRepoSetupDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF141418),
                border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.4f)),
                shadowElevation = 24.dp,
                modifier = Modifier.fillMaxWidth(0.93f).heightIn(max = 680.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(end = 34.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF58A6FF).copy(alpha = 0.14f))
                                    .border(0.8.dp, Color(0xFF58A6FF).copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Cloud, contentDescription = null, tint = Color(0xFF58A6FF), modifier = Modifier.size(19.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Setup GitHub Repository",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Signed in as @${creds.username}",
                                    color = NeonLime,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Create a new repository or link an existing one for '${project.name}'.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text("GitHub Token (PAT) *", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(5.dp))
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it; createRepoError = ""; existingRepoError = "" },
                            placeholder = { Text("ghp_xxxxxxxxxxxx", color = TextSecondary.copy(alpha = 0.5f), fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null, tint = NeonLime, modifier = Modifier.size(16.dp)) },
                            singleLine = true,
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showToken = !showToken }) {
                                    Icon(
                                        imageVector = if (showToken) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                        contentDescription = if (showToken) "Hide token" else "Show token",
                                        tint = if (showToken) NeonLime else TextSecondary
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1D1E24), unfocusedContainerColor = Color(0xFF1D1E24),
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = NeonLime, unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                cursorColor = NeonLime
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(11.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color(0xFF1D1E24))
                                .padding(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (setupTab == 0) NeonLime else Color.Transparent)
                                    .clickable { setupTab = 0; existingRepoError = "" },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Create New Repo", color = if (setupTab == 0) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (setupTab == 1) NeonLime else Color.Transparent)
                                    .clickable {
                                        setupTab = 1
                                        createRepoError = ""
                                        if (myExistingRepos.isEmpty() && !isLoadingExistingRepos) {
                                            loadExistingRepos(autoMatchSaved = false)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Existing Repo", color = if (setupTab == 1) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (setupTab == 0) {
                            Text("Repository name *", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(5.dp))
                            OutlinedTextField(
                                value = newRepoName,
                                onValueChange = { newRepoName = it; createdRepoItem = null; createRepoError = ""; showUseExistingHelper = false },
                                placeholder = { Text("e.g. my-android-app", color = TextSecondary.copy(alpha = 0.5f), fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, tint = NeonLime, modifier = Modifier.size(16.dp)) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF1D1E24), unfocusedContainerColor = Color(0xFF1D1E24),
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                    focusedBorderColor = NeonLime, unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                    cursorColor = NeonLime
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(11.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            if (createdRepoItem == null) {
                                Button(
                                    onClick = {
                                        val cleanToken = token.trim().ifEmpty { creds.token }
                                        if (cleanToken.isBlank()) {
                                            createRepoError = "Please enter your GitHub PAT token above first."
                                            return@Button
                                        }
                                        if (newRepoName.isBlank()) {
                                            createRepoError = "Repository name cannot be empty."
                                            return@Button
                                        }
                                        GitStorage.saveCredentials(context, creds.copy(token = cleanToken))
                                        isCreatingRepoNow = true
                                        createRepoError = ""
                                        showUseExistingHelper = false

                                        scope.launch(Dispatchers.IO) {
                                            val res = GitManager.createRepository(cleanToken, newRepoName.trim(), newRepoDesc, isPrivateRepo, false)
                                            withContext(Dispatchers.Main) {
                                                isCreatingRepoNow = false
                                                res.onSuccess { created ->
                                                    GitStorage.saveRepoRemote(context, project.name, created.htmlUrl, created.defaultBranch)
                                                    createdRepoItem = created
                                                    myExistingRepos = listOf(created) + myExistingRepos.filter { it.id != created.id }
                                                    repoUrl = created.htmlUrl
                                                    branch = created.defaultBranch
                                                    Toast.makeText(context, "Created '${created.fullName}' on GitHub!", Toast.LENGTH_SHORT).show()
                                                }.onFailure { err ->
                                                    val msg = err.message ?: "Failed to create repository"
                                                    createRepoError = msg
                                                    if (msg.contains("already exists", ignoreCase = true)) {
                                                        showUseExistingHelper = true
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isCreatingRepoNow,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF232820), contentColor = NeonLime),
                                    border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(42.dp)
                                ) {
                                    if (isCreatingRepoNow) {
                                        CircularProgressIndicator(color = NeonLime, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                    } else {
                                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, tint = NeonLime, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Create Repository Now", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(NeonLime.copy(alpha = 0.14f))
                                        .border(BorderStroke(1.dp, NeonLime.copy(alpha = 0.55f)), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = NeonLime, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Created & Ready: ${createdRepoItem!!.fullName}", color = NeonLime, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            if (createRepoError.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(createRepoError, color = Color(0xFFFF5252), fontSize = 11.sp, lineHeight = 15.sp)
                                if (showUseExistingHelper) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap here to select it from 'Existing Repo' →",
                                        color = Color(0xFF58A6FF),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            setupTab = 1
                                            if (myExistingRepos.isEmpty()) loadExistingRepos(autoMatchSaved = false)
                                        }
                                    )
                                }
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Target Repository URL *", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { loadExistingRepos(autoMatchSaved = false) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, tint = NeonLime, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Refresh", color = NeonLime, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            OutlinedTextField(
                                value = repoUrl,
                                onValueChange = { repoUrl = it; existingRepoError = "" },
                                placeholder = { Text("Select a repository below or paste URL", color = TextSecondary.copy(alpha = 0.5f), fontSize = 11.sp) },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF1D1E24), unfocusedContainerColor = Color(0xFF1D1E24),
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                    focusedBorderColor = NeonLime, unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                    cursorColor = NeonLime
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(11.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Your GitHub Repositories (${myExistingRepos.size})", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                if (isLoadingExistingRepos) {
                                    CircularProgressIndicator(color = NeonLime, strokeWidth = 1.8.dp, modifier = Modifier.size(13.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            if (isLoadingExistingRepos && myExistingRepos.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A1B20)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(color = NeonLime, strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Loading your repositories...", color = TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            } else if (myExistingRepos.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A1B20)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)).padding(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No repositories found. Tap Refresh or create one in 'Create New Repo'.", color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 175.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF18191F)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(11.dp)).padding(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    items(myExistingRepos, key = { it.id }) { r ->
                                        val selected = repoUrl.equals(r.htmlUrl, ignoreCase = true) || repoUrl.equals(r.cloneUrl, ignoreCase = true)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(if (selected) NeonLime.copy(alpha = 0.14f) else Color(0xFF202129))
                                                .border(BorderStroke(1.dp, if (selected) NeonLime else Color.White.copy(alpha = 0.07f)), RoundedCornerShape(9.dp))
                                                .clickable { repoUrl = r.htmlUrl; branch = r.defaultBranch; existingRepoError = "" }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Outlined.Folder, contentDescription = null, tint = if (selected) NeonLime else Color(0xFF58A6FF), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(r.name, color = if (selected) NeonLime else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(
                                                        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(if (r.isPrivate) Color(0xFFFFB020).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.1f)).padding(horizontal = 5.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(if (r.isPrivate) "Private" else "Public", color = if (r.isPrivate) Color(0xFFFFB020) else Color.LightGray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                if (!r.description.isNullOrBlank()) {
                                                    Text(r.description, color = TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            if (selected) {
                                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = NeonLime, modifier = Modifier.size(16.dp))
                                            } else {
                                                Text(r.defaultBranch, color = TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                            }
                                        }
                                    }
                                }
                            }

                            if (existingRepoError.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(existingRepoError, color = Color(0xFFFF5252), fontSize = 11.sp, lineHeight = 15.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Branch", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(5.dp))
                        OutlinedTextField(
                            value = branch,
                            onValueChange = { branch = it },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(color = NeonLime, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1D1E24), unfocusedContainerColor = Color(0xFF1D1E24),
                                focusedTextColor = NeonLime, unfocusedTextColor = NeonLime,
                                focusedBorderColor = NeonLime, unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                cursorColor = NeonLime
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(11.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { showFirstTimeRepoSetupDialog = false },
                                enabled = !isCreatingRepoNow && !isValidatingSelection,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26262E)),
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) { Text("Cancel", color = Color.White, fontSize = 13.sp) }
                            Button(
                                onClick = {
                                    val cleanToken = token.trim().ifEmpty { creds.token }
                                    if (cleanToken.isBlank()) {
                                        Toast.makeText(context, "Please enter a valid GitHub PAT token", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (setupTab == 0) {
                                        if (createdRepoItem == null) {
                                            createRepoError = "Please tap 'Create Repository Now' first, or switch to 'Existing Repo'."
                                            return@Button
                                        }
                                        val finalUrl = createdRepoItem!!.htmlUrl
                                        val finalBranch = branch.trim().ifEmpty { "main" }
                                        GitStorage.saveCredentials(context, creds.copy(token = cleanToken))
                                        GitStorage.saveRepoRemote(context, project.name, finalUrl, finalBranch)
                                        showFirstTimeRepoSetupDialog = false
                                        runFullGitHubPushAndBuild(cleanToken, finalUrl, finalBranch, commitMsg.trim(), isPrivateRepo, injectWorkflow)
                                    } else {
                                        val enteredUrl = repoUrl.trim()
                                        if (enteredUrl.isBlank() || GitManager.parseGitHubUrl(enteredUrl) == null) {
                                            existingRepoError = "Please select a repository from the list or enter a valid GitHub URL."
                                            return@Button
                                        }
                                        isValidatingSelection = true
                                        existingRepoError = ""
                                        scope.launch {
                                            val checkRes = GitManager.checkRepositoryExists(cleanToken, enteredUrl)
                                            isValidatingSelection = false
                                            checkRes.onSuccess { foundRepo ->
                                                if (foundRepo == null) {
                                                    existingRepoError = "Repository not found on GitHub. Choose an existing repo below or create a new one."
                                                } else {
                                                    val finalBranch = branch.trim().ifEmpty { foundRepo.defaultBranch }
                                                    GitStorage.saveCredentials(context, creds.copy(token = cleanToken))
                                                    GitStorage.saveRepoRemote(context, project.name, foundRepo.htmlUrl, finalBranch)
                                                    showFirstTimeRepoSetupDialog = false
                                                    runFullGitHubPushAndBuild(cleanToken, foundRepo.htmlUrl, finalBranch, commitMsg.trim(), foundRepo.isPrivate, injectWorkflow)
                                                }
                                            }.onFailure { err ->
                                                existingRepoError = err.message ?: "Failed to verify repository"
                                            }
                                        }
                                    }
                                },
                                enabled = !isCreatingRepoNow && !isValidatingSelection,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier.weight(1.45f).height(44.dp)
                            ) {
                                if (isValidatingSelection) {
                                    CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Verifying...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                } else {
                                    Text("Save & Build APK", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF23242C))
                            .clickable(enabled = !isCreatingRepoNow && !isValidatingSelection) { showFirstTimeRepoSetupDialog = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
    // ================= ৯. Commit & Build APK ডায়ালগ =================
    if (showCommitMessageDialog) {
        val creds = GitStorage.loadCredentials(context)
        val (savedUrl, savedBranch) = GitStorage.loadRepoRemote(context, project.name)

        var activeSavedUrl by remember(savedUrl) { mutableStateOf(savedUrl) }
        val displayRepo = activeSavedUrl.removePrefix("https://github.com/").removeSuffix(".git").ifBlank { "No repository linked" }
        val displayBranch = savedBranch.ifBlank { "main" }

        var commitMsg by remember { mutableStateOf("Update '${project.name}' from CodeStudio") }
        var isCheckingRepo by remember { mutableStateOf(true) }
        var isVerifyingOnPush by remember { mutableStateOf(false) }
        var repoExistsOnGitHub by remember { mutableStateOf<Boolean?>(null) }
        var repoMissingWarning by remember { mutableStateOf(false) }
        var warningText by remember { mutableStateOf("") }

        LaunchedEffect(savedUrl, creds.token) {
            isCheckingRepo = true
            repoMissingWarning = false
            warningText = ""
            if (savedUrl.isBlank()) {
                activeSavedUrl = ""
                isCheckingRepo = false
                repoExistsOnGitHub = false
                return@LaunchedEffect
            }
            val checkRes = GitManager.checkRepositoryExists(creds.token, savedUrl)
            isCheckingRepo = false
            checkRes.onSuccess { item ->
                if (item == null) {
                    GitStorage.saveRepoRemote(context, project.name, "", savedBranch)
                    activeSavedUrl = ""
                    repoExistsOnGitHub = false
                } else {
                    activeSavedUrl = item.htmlUrl
                    repoExistsOnGitHub = true
                }
            }.onFailure {
                repoExistsOnGitHub = null
            }
        }

        val blinkTransition = rememberInfiniteTransition(label = "settingsBlink")
        val pulseAlpha by blinkTransition.animateFloat(
            initialValue = 1f, targetValue = 0.32f,
            animationSpec = infiniteRepeatable(animation = tween(500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "pulseAlpha"
        )
        val pulseScale by blinkTransition.animateFloat(
            initialValue = 1f, targetValue = 1.07f,
            animationSpec = infiniteRepeatable(animation = tween(500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "pulseScale"
        )
        val pulseAccent by blinkTransition.animateColor(
            initialValue = Color(0xFFFFB020), targetValue = Color(0xFFFF5252),
            animationSpec = infiniteRepeatable(animation = tween(500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "pulseColor"
        )

        Dialog(
            onDismissRequest = { if (!isVerifyingOnPush) showCommitMessageDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF131318),
                border = BorderStroke(1.dp, if (repoMissingWarning) Color(0xFFFFB020).copy(alpha = 0.65f) else Color.White.copy(alpha = 0.14f)),
                shadowElevation = 24.dp,
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(end = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(NeonLime.copy(alpha = 0.14f)).border(0.8.dp, NeonLime.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = NeonLime, modifier = Modifier.size(19.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Commit & Build APK", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("GitHub Cloud CI/CD", color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("TARGET REPOSITORY", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                            val settingsBg = if (repoMissingWarning) pulseAccent.copy(alpha = 0.22f) else Color(0xFF1E222B)
                            val settingsBorder = if (repoMissingWarning) pulseAccent else Color(0xFF58A6FF).copy(alpha = 0.48f)
                            val settingsTint = if (repoMissingWarning) pulseAccent else Color(0xFF58A6FF)

                            Row(
                                modifier = Modifier
                                    .scale(if (repoMissingWarning) pulseScale else 1f)
                                    .alpha(if (repoMissingWarning) pulseAlpha else 1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(settingsBg)
                                    .border(1.dp, settingsBorder, RoundedCornerShape(9.dp))
                                    .clickable(enabled = !isVerifyingOnPush) {
                                        showCommitMessageDialog = false
                                        showFirstTimeRepoSetupDialog = true
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Settings, contentDescription = "Repository Settings", tint = settingsTint, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Settings", color = if (repoMissingWarning) Color.White else Color(0xFF8EC5FF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(7.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1A1B21))
                                .border(1.dp, if (repoExistsOnGitHub == false) Color(0xFFFFB020).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Folder, contentDescription = null, tint = if (repoExistsOnGitHub == true) Color(0xFF58A6FF) else TextSecondary, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isCheckingRepo) "Checking linked repository..." else displayRepo,
                                    color = if (repoExistsOnGitHub == true) Color(0xFF79C0FF) else TextSecondary,
                                    fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                                )
                                if (repoExistsOnGitHub == true) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(NeonLime.copy(alpha = 0.14f)).border(0.6.dp, NeonLime.copy(alpha = 0.45f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                                        Text(displayBranch, color = NeonLime, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(7.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when {
                                    isCheckingRepo -> {
                                        CircularProgressIndicator(color = Color(0xFF58A6FF), strokeWidth = 1.6.dp, modifier = Modifier.size(10.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Verifying repository on GitHub...", color = TextSecondary, fontSize = 10.sp)
                                    }
                                    repoExistsOnGitHub == true -> {
                                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(NeonLime))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Repository verified & ready", color = NeonLime, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                    }
                                    repoExistsOnGitHub == false -> {
                                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFFFB020)))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("No repository set — create or select from Settings", color = Color(0xFFFFB020), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    else -> {
                                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(TextSecondary))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Will verify repository before pushing", color = TextSecondary, fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = repoMissingWarning) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFB020).copy(alpha = 0.12f)).border(1.dp, Color(0xFFFFB020).copy(alpha = 0.55f), RoundedCornerShape(12.dp)).padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Outlined.Warning, contentDescription = "Warning", tint = Color(0xFFFFB020), modifier = Modifier.padding(top = 1.dp).size(17.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Repository Required!", color = Color(0xFFFFCC66), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = warningText.ifBlank { "No repository found on GitHub. Tap the Settings button above to create a new repository or link an existing one." },
                                            color = Color(0xFFEAEAEA), fontSize = 11.sp, lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("COMMIT MESSAGE", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                            if (commitMsg.isNotEmpty()) {
                                Text("Clear", color = Color(0xFF8EC5FF), fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { commitMsg = "" }.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = commitMsg,
                            onValueChange = { commitMsg = it },
                            placeholder = { Text("Describe your changes...", color = TextSecondary.copy(alpha = 0.5f), fontSize = 12.sp) },
                            maxLines = 2,
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp, lineHeight = 18.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1A1B21), unfocusedContainerColor = Color(0xFF1A1B21),
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = NeonLime, unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                                cursorColor = NeonLime
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { showCommitMessageDialog = false },
                                enabled = !isVerifyingOnPush,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF23242C), contentColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) { Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.Medium) }

                            Button(
                                onClick = {
                                    if (activeSavedUrl.isBlank() || repoExistsOnGitHub == false) {
                                        repoExistsOnGitHub = false
                                        repoMissingWarning = true
                                        warningText = "Please create a new repository or choose an existing repository from the Settings button above before pushing."
                                        return@Button
                                    }
                                    isVerifyingOnPush = true
                                    repoMissingWarning = false
                                    scope.launch {
                                        val verifyRes = GitManager.checkRepositoryExists(creds.token, activeSavedUrl)
                                        isVerifyingOnPush = false
                                        verifyRes.onSuccess { existingRepo ->
                                            if (existingRepo == null) {
                                                GitStorage.saveRepoRemote(context, project.name, "", savedBranch)
                                                activeSavedUrl = ""
                                                repoExistsOnGitHub = false
                                                repoMissingWarning = true
                                                warningText = "The linked repository no longer exists on GitHub. Tap the Settings button above to create or select a repository."
                                            } else {
                                                repoExistsOnGitHub = true
                                                showCommitMessageDialog = false
                                                runFullGitHubPushAndBuild(
                                                    customUrlOverride = existingRepo.htmlUrl,
                                                    commitMsgOverride = commitMsg.trim().ifEmpty { "Update '${project.name}' from CodeStudio" }
                                                )
                                            }
                                        }.onFailure { err ->
                                            repoMissingWarning = true
                                            warningText = "Could not verify repository: ${err.message ?: "Check internet connection or token in Settings."}"
                                        }
                                    }
                                },
                                enabled = !isVerifyingOnPush && !isCheckingRepo,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.45f).height(44.dp)
                            ) {
                                if (isVerifyingOnPush) {
                                    CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Checking...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                } else {
                                    Text("Push & Build", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF23242C))
                            .clickable(enabled = !isVerifyingOnPush) { showCommitMessageDialog = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }

    // ================= ১০. বিল্ড কনসোল টার্মিনাল ডায়ালগ =================
    if (showBuildTerminal) {
        val terminalScrollState = rememberLazyListState()

        LaunchedEffect(buildLogs.size) {
            if (buildLogs.isNotEmpty()) {
                terminalScrollState.animateScrollToItem(buildLogs.lastIndex)
            }
        }

        Dialog(
            onDismissRequest = {
                if (isLocalBuildMode) cancelLocalBuildFlag.set(true)
                buildPollingJob?.cancel()
                isBuilding = false
                showBuildTerminal = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0F0F12),
                border = BorderStroke(
                    1.dp,
                    when (buildSuccess) {
                        true -> NeonLime.copy(alpha = 0.6f)
                        false -> Color(0xFFFF5252).copy(alpha = 0.55f)
                        null -> Color.White.copy(alpha = 0.16f)
                    }
                ),
                modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.78f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(end = 34.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Terminal, contentDescription = null, tint = NeonLime, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(buildTitle, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (isBuilding) {
                                CircularProgressIndicator(color = NeonLime, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyColumn(
                            state = terminalScrollState,
                            modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF070709)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(buildLogs) { line ->
                                val color = when {
                                    line.contains("SUCCESSFUL", ignoreCase = true) || line.contains("SUCCESS:", ignoreCase = true) || line.contains("UP-TO-DATE") -> NeonLime
                                    line.contains("ERROR", ignoreCase = true) || line.contains("FAILED", ignoreCase = true) -> Color(0xFFFF5252)
                                    line.contains("WARN", ignoreCase = true) || line.contains("NOTE:", ignoreCase = true) -> Color(0xFFFFB020)
                                    line.contains("[Auto-Pilot]", ignoreCase = true) -> Color(0xFF00E5FF)
                                    line.contains("GitHub Runner", ignoreCase = true) -> Color(0xFF58A6FF)
                                    line.contains("> Task") -> Color(0xFFC586C0)
                                    line.startsWith("[") -> Color(0xFF64B5F6)
                                    else -> Color.LightGray
                                }
                                Text(line, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isLocalBuildMode && buildSuccess == true && localBuiltApkPath.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val (_, msg) = ApkInstaller.installApk(context, localBuiltApkPath)
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.weight(1.35f).height(42.dp)
                                ) {
                                    Icon(Icons.Default.Android, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Install APK", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val exported = exportApkToDownloads(context, localBuiltApkPath, project.name, selectedNativeVariant)
                                            if (exported != null) {
                                                Toast.makeText(context, "Saved to $exported", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Failed to export APK", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.55f)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.weight(1f).height(42.dp)
                                ) {
                                    Icon(Icons.Outlined.Download, contentDescription = null, tint = NeonLime, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Export", color = NeonLime, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        } else if (isLocalBuildMode && !isBuilding && buildSuccess == false) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        showBuildTerminal = false
                                        showEngineManager = true
                                    },
                                    border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.45f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) {
                                    Icon(Icons.Outlined.Build, contentDescription = null, tint = NeonLime, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Engine Manager", color = NeonLime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        runNativeInProcessBuildEngine(selectedNativeVariant, cleanFirst = true)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282A34), contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Clean Rebuild", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else if (!isLocalBuildMode && (activeActionsUrl.isNotEmpty() || completedRunId != null)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (activeActionsUrl.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activeActionsUrl))) },
                                        border = BorderStroke(1.dp, Color(0xFF58A6FF).copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(38.dp)
                                    ) {
                                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = Color(0xFF58A6FF), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open Actions", color = Color(0xFF58A6FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (completedRunId != null) {
                                    Button(
                                        onClick = {
                                            val token = GitStorage.loadCredentials(context).token
                                            val runId = completedRunId ?: return@Button
                                            isDownloadingApk = true
                                            scope.launch(Dispatchers.IO) {
                                                val dlRes = GitManager.downloadWorkflowApk(context, token, activeGitOwner, activeGitRepo, runId)
                                                withContext(Dispatchers.Main) {
                                                    isDownloadingApk = false
                                                    dlRes.onSuccess { savedPath ->
                                                        Toast.makeText(context, "APK Saved: $savedPath", Toast.LENGTH_SHORT).show()
                                                    }.onFailure { err ->
                                                        Toast.makeText(context, err.message ?: "Download failed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isDownloadingApk,
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(38.dp)
                                    ) {
                                        if (isDownloadingApk) {
                                            CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
                                        } else {
                                            Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Download APK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(buildLogs.joinToString("\n")))
                                    Toast.makeText(context, "Build logs copied!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Logs", color = TextSecondary, fontSize = 11.sp)
                            }

                            if (buildSuccess == true) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = NeonLime, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Ready", color = NeonLime, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            Button(
                                onClick = {
                                    if (isLocalBuildMode) cancelLocalBuildFlag.set(true)
                                    buildPollingJob?.cancel()
                                    isBuilding = false
                                    showBuildTerminal = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (buildSuccess == true) NeonLime else Color(0xFF26262E)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = if (isBuilding) "Stop" else "Done",
                                    color = if (buildSuccess == true) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF23242C))
                            .clickable {
                                if (isLocalBuildMode) cancelLocalBuildFlag.set(true)
                                buildPollingJob?.cancel()
                                isBuilding = false
                                showBuildTerminal = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

private suspend fun exportApkToDownloads(
    context: Context,
    apkPath: String,
    projectName: String,
    variant: EngineBuildVariant
): String? = withContext(Dispatchers.IO) {
    val srcFile = File(apkPath)
    if (!srcFile.exists() || !srcFile.isFile) return@withContext null

    val safeName = projectName.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "App" }
    val outFileName = "${safeName}-${variant.label.lowercase()}.apk"
    val buffer = ByteArray(65_536)

    return@withContext try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, outFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CodeStudio_APKs")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
            resolver.openOutputStream(uri)?.use { out ->
                BufferedInputStream(FileInputStream(srcFile), 65_536).use { bis ->
                    BufferedOutputStream(out, 65_536).use { bos ->
                        var r: Int
                        while (bis.read(buffer).also { r = it } > 0) {
                            bos.write(buffer, 0, r)
                        }
                    }
                }
            }
            "Downloads/CodeStudio_APKs/$outFileName"
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CodeStudio_APKs")
            if (!dir.exists()) dir.mkdirs()
            val dest = File(dir, outFileName)
            srcFile.copyTo(dest, overwrite = true)
            dest.absolutePath
        }
    } catch (_: Exception) {
        null
    }
}