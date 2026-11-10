package com.shd.codestudio

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen() {
    val context = LocalContext.current

    var projects by remember { mutableStateOf(loadProjects(context)) }
    
    LaunchedEffect(projects) {
        saveProjects(context, projects)
    }
    
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var projectForMenu by remember { mutableStateOf<Project?>(null) }
    var projectToDelete by remember { mutableStateOf<Project?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showZipPickerDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var openedProject by remember { mutableStateOf<Project?>(null) }
    
    // নতুন: টার্মিনাল ডায়ালগ দেখানোর জন্য
    var showTerminal by remember { mutableStateOf(false) }

    var hasPermission by remember { mutableStateOf(hasStoragePermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasPermission = hasStoragePermission(context)
        if (hasPermission) { showZipPickerDialog = true }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            val projectName = "FolderProject_${System.currentTimeMillis() % 1000}"
            projects = projects + Project(projectName, "Android • Folder", Color(0xFF00C2A8))
            Toast.makeText(context, "Folder Imported!", Toast.LENGTH_SHORT).show()
        }
    }

    val filteredProjects = if (searchQuery.isBlank()) projects else projects.filter { it.name.contains(searchQuery, ignoreCase = true) }

    Column(
        modifier = Modifier.fillMaxSize().background(DarkBackground).padding(horizontal = 16.dp).systemBarsPadding()
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        if (!isSearchActive) {
            Box(modifier = Modifier.fillMaxWidth().shadow(elevation = 16.dp, shape = RoundedCornerShape(24.dp), ambientColor = Color(0xFFA855F7), spotColor = Color(0xFFA855F7)).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(colors = listOf(Color(0xFFA855F7), Color(0xFF7C3AED)))).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(46.dp).shadow(4.dp, RoundedCornerShape(14.dp)).background(Color.White, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text("</>", color = Color(0xFF7C3AED), fontSize = 18.sp, fontWeight = FontWeight.Black) }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CodeStudio", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Your projects", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                    Box(modifier = Modifier.size(42.dp).background(Color.White.copy(alpha = 0.2f), CircleShape).clickable { isSearchActive = true }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Search, contentDescription = "Search", tint = Color.White) }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.size(42.dp).background(Color.White.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Menu, contentDescription = "Menu", tint = Color.White) }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { isSearchActive = false; searchQuery = "" }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) }
                TextField(
                    value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text("Search project...", color = TextSecondary) }, singleLine = true,
                    colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = NeonLime),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                )
                if (searchQuery.isNotEmpty()) { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White) } }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!isSearchActive) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(text = "Create project", icon = Icons.Default.Add, modifier = Modifier.weight(1f), onClick = { showCreateDialog = true })
                PillButton(text = "Import", modifier = Modifier.weight(1f), onClick = { showImportDialog = true })
            }
            Spacer(modifier = Modifier.height(12.dp))
            PillButton(text = "Clone Git Repository", modifier = Modifier.fillMaxWidth(), onClick = { Toast.makeText(context, "Clone Repo", Toast.LENGTH_SHORT).show() })
            
            Spacer(modifier = Modifier.height(12.dp))
            // নতুন টেস্ট বাটন
            Button(
                onClick = { showTerminal = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C2A8), contentColor = Color.Black),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("Test Linux Terminal (PRoot)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            
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
                modifier = Modifier.fillMaxSize().padding(top = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.size(140.dp).clip(CircleShape).background(NeonLime.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(90.dp).clip(CircleShape).background(SurfaceDark), contentAlignment = Alignment.Center) {
                        Text("</>", color = NeonLime, fontSize = 32.sp, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("No projects yet", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Your ideas deserve an app.\nCreate your first Android project and start building something amazing.", color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
                Spacer(modifier = Modifier.height(24.dp))
                PillButton(text = "Create your first project", icon = Icons.Default.Add, modifier = Modifier.fillMaxWidth(0.8f), onClick = { showCreateDialog = true })
            }
        } else if (filteredProjects.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { Text("No projects found", color = TextSecondary, fontSize = 16.sp) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filteredProjects) { project ->
                    Box(modifier = Modifier.clickable { openedProject = project }) {
                        PremiumProjectCard(project = project, onMenuClick = { projectForMenu = project })
                    }
                }
            }
        }
    }

    // ================= ডায়ালগসমূহ =================

    // টার্মিনাল ডায়ালগ
    if (showTerminal) {
        TerminalScreen(onDismiss = { showTerminal = false })
    }

    if (projectForMenu != null) {
        ProjectMenuDialog(project = projectForMenu!!, onDismiss = { projectForMenu = null }, onDeleteClick = { projectToDelete = projectForMenu; projectForMenu = null })
    }
    if (projectToDelete != null) {
        DeleteConfirmationDialog(projectName = projectToDelete!!.name, onDismiss = { projectToDelete = null }, onConfirmDelete = { projects = projects.filter { it != projectToDelete }; projectToDelete = null })
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
                val projectName = zipItem.name.removeSuffix(".zip")
                Toast.makeText(context, "Extracting ${zipItem.name}...", Toast.LENGTH_SHORT).show()
                if (unzipFile(context, zipItem.uri, projectName)) {
                    projects = projects + Project(projectName, "Android • Imported", Color(0xFFFFB020))
                    Toast.makeText(context, "Project Imported Successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to unzip!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    
    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, pkg, lang, minSdk, targetSdk ->
                createDefaultProjectFiles(context, name, pkg, lang, minSdk, targetSdk)
                val newProject = Project(name, "Android • $lang • API $minSdk", NeonLime)
                projects = projects + newProject
                showCreateDialog = false
                openedProject = newProject
                Toast.makeText(context, "Project '$name' created!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (openedProject != null) {
        EditorScreen(
            project = openedProject!!,
            onBack = { openedProject = null }
        )
    }
}

@Composable
fun PillButton(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
        shape = RoundedCornerShape(
            topStart = 30.dp,    
            bottomStart = 8.dp,  
            topEnd = 8.dp,       
            bottomEnd = 30.dp    
        ),
        modifier = modifier.height(54.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}