package com.shd.codestudio.homescreen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shd.codestudio.DarkBackground
import com.shd.codestudio.NeonLime
import com.shd.codestudio.SurfaceDark
import com.shd.codestudio.TextPrimary
import com.shd.codestudio.TextSecondary
import com.shd.codestudio.git.CloneRepositoryScreen
import com.shd.codestudio.git.GitManager
import com.shd.codestudio.models.Project
import com.shd.codestudio.nativeengine.EngineManagerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun HomeScreen(
    onOpenProject: (Project) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var projects by remember { mutableStateOf(loadProjects(context)) }

    LaunchedEffect(Unit) {
        projects = loadProjects(context)
    }

    LaunchedEffect(projects) {
        saveProjects(context, projects)
    }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var projectForMenu by remember { mutableStateOf<Project?>(null) }
    var projectToDelete by remember { mutableStateOf<Project?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showZipPickerDialog by remember { mutableStateOf(false) }
    var showHomeMenu by remember { mutableStateOf(false) }
    var showEngineManager by remember { mutableStateOf(false) }

    var showCreateProjectPage by remember { mutableStateOf(false) }
    var showCloneScreen by remember { mutableStateOf(false) }

    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf(ImportProgress()) }
    var importTitle by remember { mutableStateOf("Importing project...") }
    var isCancellingImport by remember { mutableStateOf(false) }
    val cancelImportFlag = remember { AtomicBoolean(false) }

    DisposableEffect(isImporting) {
        val window = (context as? Activity)?.window
        if (isImporting) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var hasPermission by remember { mutableStateOf(hasStoragePermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasPermission = hasStoragePermission(context)
        if (hasPermission) { showZipPickerDialog = true }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            cancelImportFlag.set(false)
            isCancellingImport = false
            importTitle = "Importing Folder..."
            importProgress = ImportProgress(currentItem = "Scanning folder...")
            isImporting = true

            scope.launch(Dispatchers.IO) {
                val result = importFolderStreaming(
                    context = context,
                    treeUri = uri,
                    isCancelled = { cancelImportFlag.get() },
                    onProgress = { progress ->
                        scope.launch(Dispatchers.Main) {
                            importProgress = progress
                        }
                    }
                )
                withContext(Dispatchers.Main) {
                    isImporting = false
                    isCancellingImport = false
                    when (result.status) {
                        ImportStatus.SUCCESS -> {
                            val newProj = Project(result.projectName, "Android • Folder", Color(0xFF00C2A8), isPinned = false)
                            projects = listOf(newProj) + projects.filter { it.name != result.projectName }
                            saveProjects(context, projects)
                            Toast.makeText(
                                context,
                                "Imported ${result.filesCount} files (${formatSize(result.bytesCopied)})",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        ImportStatus.CANCELLED -> {
                            Toast.makeText(context, "Folder import cancelled", Toast.LENGTH_SHORT).show()
                        }
                        ImportStatus.INSUFFICIENT_SPACE -> {
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                        ImportStatus.ERROR -> {
                            Toast.makeText(context, result.message.ifEmpty { "Failed to import folder!" }, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    // ফুল-স্ক্রিন CreateProjectPage প্রদর্শন
    if (showCreateProjectPage) {
        CreateProjectPage(
            onBack = { showCreateProjectPage = false },
            onCreate = { name, pkg, lang, minSdk, targetSdk ->
                val cleanName = name.trim()
                val newProject = Project(cleanName, "Android • $lang • API $minSdk", NeonLime, isPinned = false)
                projects = listOf(newProject) + projects.filter { it.name != cleanName }
                saveProjects(context, projects)
                showCreateProjectPage = false
                Toast.makeText(context, "Project '$cleanName' created!", Toast.LENGTH_SHORT).show()
            }
        )
        return
    }

    // ফুল-স্ক্রিন CloneRepositoryScreen প্রদর্শন
    if (showCloneScreen) {
        CloneRepositoryScreen(
            onBack = { showCloneScreen = false },
            onStartClone = { repoUrl, folderName, branch, token ->
                showCloneScreen = false
                cancelImportFlag.set(false)
                isCancellingImport = false
                importTitle = "Cloning '$folderName'..."
                importProgress = ImportProgress(currentItem = "Connecting to GitHub...")
                isImporting = true

                scope.launch(Dispatchers.IO) {
                    val result = GitManager.cloneRepository(
                        context = context,
                        repoUrl = repoUrl,
                        targetFolderName = folderName,
                        branch = branch,
                        token = token,
                        isCancelled = { cancelImportFlag.get() },
                        onProgress = { progress ->
                            scope.launch(Dispatchers.Main) {
                                importProgress = progress
                            }
                        }
                    )
                    withContext(Dispatchers.Main) {
                        isImporting = false
                        isCancellingImport = false
                        if (result.success) {
                            val newProj = Project(folderName, "Android • Git Clone", Color(0xFFA855F7), isPinned = false)
                            projects = listOf(newProj) + projects.filter { it.name != folderName }
                            saveProjects(context, projects)
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
        return
    }

    // পিন করা প্রজেক্টগুলো সবসময় শীর্ষে থাকবে
    val sortedProjects = remember(projects) {
        projects.sortedWith(compareByDescending<Project> { it.isPinned })
    }

    val filteredProjects = if (searchQuery.isBlank()) sortedProjects else sortedProjects.filter { it.name.contains(searchQuery, ignoreCase = true) }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .systemBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            if (!isSearchActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 16.dp, shape = RoundedCornerShape(24.dp), ambientColor = Color(0xFFA855F7), spotColor = Color(0xFFA855F7))
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(colors = listOf(Color(0xFFA855F7), Color(0xFF7C3AED))))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(46.dp).shadow(4.dp, RoundedCornerShape(14.dp)).background(Color.White, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                            Text("</>", color = Color(0xFF7C3AED), fontSize = 18.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("CodeStudio", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Your projects", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        }
                        Box(modifier = Modifier.size(42.dp).background(Color.White.copy(alpha = 0.2f), CircleShape).clickable { isSearchActive = true }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                    .clickable { showHomeMenu = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Menu", tint = Color.White)
                            }

                            DropdownMenu(
                                expanded = showHomeMenu,
                                onDismissRequest = { showHomeMenu = false },
                                modifier = Modifier
                                    .background(Color(0xFF1A1B22), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Native Engine Manager", color = NeonLime, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Outlined.Build, contentDescription = null, tint = NeonLime, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showHomeMenu = false
                                        showEngineManager = true
                                    }
                                )
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                DropdownMenuItem(
                                    text = { Text("Refresh Projects", color = Color.White, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showHomeMenu = false
                                        projects = loadProjects(context)
                                        Toast.makeText(context, "Project list refreshed!", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search project...", color = TextSecondary) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = NeonLime
                        ),
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (!isSearchActive) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(text = "Create project", icon = Icons.Default.Add, modifier = Modifier.weight(1f), onClick = { showCreateProjectPage = true })
                    PillButton(text = "Import", modifier = Modifier.weight(1f), onClick = { showImportDialog = true })
                }
                Spacer(modifier = Modifier.height(12.dp))

                PillButton(
                    text = "Clone Git Repository",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showCloneScreen = true }
                )

                Spacer(modifier = Modifier.height(28.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val titleText = if (filteredProjects.size > 1) "Your projects" else "Your project"
                Text(if (isSearchActive) "Search Result" else titleText, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                if (!isSearchActive) {
                    Text("${filteredProjects.size}", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.background(NeonLime, CircleShape).padding(horizontal = 10.dp, vertical = 2.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (filteredProjects.isEmpty() && !isSearchActive) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(130.dp).clip(CircleShape).background(NeonLime.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(SurfaceDark), contentAlignment = Alignment.Center) {
                            Text("</>", color = NeonLime, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("No projects yet", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Your ideas deserve an app.\nCreate your first Android project and start building something amazing.", color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    PillButton(text = "Create your first project", icon = Icons.Default.Add, modifier = Modifier.fillMaxWidth(0.8f), onClick = { showCreateProjectPage = true })
                }
            } else if (filteredProjects.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("No projects found", color = TextSecondary, fontSize = 16.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(filteredProjects, key = { it.name }) { project ->
                        Box(modifier = Modifier.clickable { onOpenProject(project) }) {
                            PremiumProjectCard(project = project, onMenuClick = { projectForMenu = project })
                        }
                    }
                }
            }
        }

        // ================= স্ট্রিমিং ইমপোর্ট ও গিট প্রোগ্রেস ডায়ালগ =================
        if (isImporting) {
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF121216),
                    border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = importTitle,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        val totalB = importProgress.totalBytes
                        if (totalB > 0L) {
                            val fraction = (importProgress.bytesCopied.toFloat() / totalB.toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { fraction },
                                color = NeonLime,
                                trackColor = Color(0xFF282830),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${(fraction * 100).toInt()}% • ${formatSize(importProgress.bytesCopied)} / ${formatSize(totalB)}",
                                color = NeonLime,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            LinearProgressIndicator(
                                color = NeonLime,
                                trackColor = Color(0xFF282830),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${formatSize(importProgress.bytesCopied)} copied",
                                color = NeonLime,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${importProgress.filesCount} files processed",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                        if (importProgress.currentItem.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = importProgress.currentItem,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                isCancellingImport = true
                                cancelImportFlag.set(true)
                            },
                            enabled = !isCancellingImport,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2A1B1B),
                                contentColor = Color(0xFFFF5252)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Text(
                                text = if (isCancellingImport) "Cancelling & Cleaning up..." else "Cancel Import",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEngineManager) {
        EngineManagerDialog(
            recommendedApi = 36,
            onDismiss = { showEngineManager = false }
        )
    }

    if (projectForMenu != null) {
        ProjectMenuDialog(
            project = projectForMenu!!,
            onDismiss = { projectForMenu = null },
            onTogglePin = {
                val target = projectForMenu!!
                val toggled = target.copy(isPinned = !target.isPinned)
                projects = projects.map { if (it.name == target.name) toggled else it }
                saveProjects(context, projects)
                Toast.makeText(context, if (toggled.isPinned) "Pinned to top!" else "Project unpinned", Toast.LENGTH_SHORT).show()
            },
            onDeleteClick = { projectToDelete = projectForMenu; projectForMenu = null }
        )
    }
    if (projectToDelete != null) {
        DeleteConfirmationDialog(
            projectName = projectToDelete!!.name,
            onDismiss = { projectToDelete = null },
            onConfirmDelete = {
                val targetName = projectToDelete!!.name
                projects = projects.filter { it != projectToDelete }
                saveProjects(context, projects)
                projectToDelete = null
                scope.launch(Dispatchers.IO) {
                    getProjectDirectory(context, targetName).deleteRecursively()
                }
            }
        )
    }
    if (showImportDialog) {
        ImportOptionsDialog(
            onDismiss = { showImportDialog = false },
            onPickFolder = { showImportDialog = false; folderPickerLauncher.launch(null) },
            onPickZip = {
                showImportDialog = false
                if (hasPermission) {
                    showZipPickerDialog = true
                } else {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:" + context.packageName)
                        permissionLauncher.launch(intent)
                    } catch (e: Exception) {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        permissionLauncher.launch(intent)
                    }
                }
            }
        )
    }
    if (showZipPickerDialog) {
        ZipPickerDialog(
            onDismiss = { showZipPickerDialog = false },
            onZipSelected = { zipItem ->
                showZipPickerDialog = false
                val projectName = zipItem.name.removeSuffix(".zip").trim()
                cancelImportFlag.set(false)
                isCancellingImport = false
                importTitle = "Extracting ZIP (${formatSize(zipItem.size)})"
                importProgress = ImportProgress(totalBytes = zipItem.size, currentItem = zipItem.name)
                isImporting = true

                scope.launch(Dispatchers.IO) {
                    val result = unzipFileStreaming(
                        context = context,
                        zipUri = zipItem.uri,
                        projectName = projectName,
                        totalZipBytes = zipItem.size,
                        isCancelled = { cancelImportFlag.get() },
                        onProgress = { progress ->
                            scope.launch(Dispatchers.Main) {
                                importProgress = progress
                            }
                        }
                    )
                    val projDir = getProjectDirectory(context, projectName)
                    withContext(Dispatchers.Main) {
                        isImporting = false
                        isCancellingImport = false
                        when (result.status) {
                            ImportStatus.SUCCESS -> {
                                if (projDir.exists()) {
                                    val newProj = Project(projectName, "Android • Imported", Color(0xFFFFB020), isPinned = false)
                                    projects = listOf(newProj) + projects.filter { it.name != projectName }
                                    saveProjects(context, projects)
                                    Toast.makeText(
                                        context,
                                        "Imported ${result.filesCount} files (${formatSize(result.bytesCopied)})",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            ImportStatus.CANCELLED -> {
                                Toast.makeText(context, "ZIP import cancelled", Toast.LENGTH_SHORT).show()
                            }
                            ImportStatus.INSUFFICIENT_SPACE -> {
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            }
                            ImportStatus.ERROR -> {
                                Toast.makeText(context, result.message.ifEmpty { "Failed to unzip archive!" }, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        )
    }
}