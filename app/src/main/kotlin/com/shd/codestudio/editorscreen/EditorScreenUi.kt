package com.shd.codestudio.editorscreen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shd.codestudio.DarkBackground
import com.shd.codestudio.NeonLime
import com.shd.codestudio.TextSecondary
import com.shd.codestudio.models.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun EditorScreen(
    project: Project,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 0 = DOC Files, 1 = Code, 2 = Live Preview
    var currentPage by remember { mutableIntStateOf(0) }

    val codeEditorController = remember { CodeEditorController() }

    DisposableEffect(Unit) {
        onDispose {
            codeEditorController.destroy()
        }
    }

    var selectedFile by remember(project.name) { mutableStateOf("") }
    var projectFiles by remember(project.name) { mutableStateOf<List<String>>(emptyList()) }

    var expandedFolders by remember(project.name) {
        mutableStateOf(
            setOf(
                "app",
                "app/src",
                "app/src/main",
                "app/src/main/kotlin",
                "app/src/main/java",
                "app/src/main/cpp",
                "app/src/main/res"
            )
        )
    }

    var androidExpanded by remember(project.name) {
        mutableStateOf(
            setOf(
                "app",
                "app/src/main/manifests",
                "app/src/main/kotlin",
                "app/src/main/java",
                "app/src/main/cpp",
                "__external_build__",
                "app/src/main/res",
                "__res_mipmap__",
                "app/src/main/res/values",
                "__gradle_scripts__"
            )
        )
    }
    val treeListState = remember(project.name) { LazyListState() }

    var refreshKey by remember { mutableIntStateOf(0) }
    var fontScale by remember { mutableFloatStateOf(1f) }
    var editorFontSize by remember { mutableFloatStateOf(13f) }
    var showExitDialog by remember { mutableStateOf(false) }
    var isKeyboardActiveState by remember { mutableStateOf(false) }

    LaunchedEffect(project.name, refreshKey) {
        val files = withContext(Dispatchers.IO) {
            getAllProjectFiles(context, project.name).sorted()
        }
        projectFiles = files
        if (selectedFile.isEmpty() || !files.contains(selectedFile)) {
            selectedFile = files.find { it.endsWith("MainActivity.kt") || it.endsWith("MainActivity.java") }
                ?: files.find { it.endsWith("AndroidManifest.xml") }
                ?: files.firstOrNull()
                ?: ""
        }
    }

    BackHandler {
        showExitDialog = true
    }

    LaunchedEffect(currentPage) {
        if (currentPage != 1) {
            keyboardController?.hide()
            isKeyboardActiveState = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .systemBarsPadding()
    ) {
        // পার্পল গ্রেডিয়েন্ট হেডার (ক্লিন — কোনো Push বাটন নেই)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(22.dp),
                    ambientColor = Color(0xFFA855F7),
                    spotColor = Color(0xFFA855F7)
                )
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(colors = listOf(Color(0xFFA855F7), Color(0xFF7C3AED))))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { showExitDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(4.dp, RoundedCornerShape(12.dp))
                        .background(Color.White, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "</>",
                        color = Color(0xFF7C3AED),
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CodeStudio",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Android IDE & App Builder",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // ৩-ট্যাব বার
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFF1E1E1E))
                .padding(2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf("DOC Files", "</> Code", "Live Preview")
            tabs.forEachIndexed { index, title ->
                val isSelected = currentPage == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (isSelected) NeonLime else Color.Transparent)
                        .clickable {
                            if (index != 1) {
                                keyboardController?.hide()
                                isKeyboardActiveState = false
                            }
                            currentPage = index
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = LocalTextStyle.current.copy(
                            color = if (isSelected) Color.Black else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 10.sp,
                            lineHeight = 10.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }
            }
        }

        // প্রতিটি পেজ আলাদাভাবে রেন্ডার
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(currentPage) {
                    if (currentPage == 1) return@pointerInput
                    val swipeThresholdPx = 56.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalDx = 0f
                        var totalDy = 0f
                        var triggered = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = change.position - change.previousPosition
                            totalDx += delta.x
                            totalDy += delta.y
                            if (!triggered &&
                                abs(totalDx) > swipeThresholdPx &&
                                abs(totalDx) > abs(totalDy) * 1.5f
                            ) {
                                if (currentPage == 0 && totalDx < -swipeThresholdPx) {
                                    triggered = true
                                    currentPage = 1
                                } else if (currentPage == 2 && totalDx > swipeThresholdPx) {
                                    triggered = true
                                    currentPage = 1
                                }
                            }
                        }
                    }
                }
        ) {
            when (currentPage) {
                0 -> {
                    DocFilesPage(
                        project = project,
                        selectedFile = selectedFile,
                        expandedFolders = expandedFolders,
                        androidExpanded = androidExpanded,
                        treeListState = treeListState,
                        fontScale = fontScale,
                        refreshKey = refreshKey,
                        onFolderToggle = { path ->
                            expandedFolders = if (expandedFolders.contains(path)) expandedFolders - path else expandedFolders + path
                        },
                        onExpandAll = { paths -> expandedFolders = paths + "" },
                        onCollapseAll = { expandedFolders = setOf("") },
                        onAndroidFolderToggle = { path ->
                            androidExpanded = if (androidExpanded.contains(path)) androidExpanded - path else androidExpanded + path
                        },
                        onAndroidExpandAll = { paths -> androidExpanded = paths + "" },
                        onAndroidCollapseAll = { androidExpanded = emptySet() },
                        onFileSelect = { newFile ->
                            selectedFile = newFile
                            currentPage = 1
                        },
                        onRefresh = { refreshKey++ },
                        onZoomIn = { if (fontScale < 1.4f) fontScale += 0.1f },
                        onZoomOut = { if (fontScale > 0.5f) fontScale -= 0.1f }
                    )
                }
                1 -> {
                    CodePage(
                        projectName = project.name,
                        filePath = selectedFile,
                        allProjectFiles = projectFiles,
                        editorFontSize = editorFontSize,
                        onFontSizeChange = { newSize -> editorFontSize = newSize },
                        isKeyboardActive = isKeyboardActiveState,
                        onKeyboardToggle = {
                            if (currentPage == 1) {
                                isKeyboardActiveState = !isKeyboardActiveState
                            }
                        },
                        onFileSelected = { newFile -> selectedFile = newFile },
                        onBackToFiles = {
                            keyboardController?.hide()
                            isKeyboardActiveState = false
                            currentPage = 0
                        },
                        editorController = codeEditorController
                    )
                }
                2 -> {
                    LivePreviewPage(
                        project = project,
                        fileCount = projectFiles.size,
                        onBackToCode = {
                            codeEditorController.autoSaveIfDirty()
                            currentPage = 1
                        }
                    )
                }
            }
        }
    }

    if (showExitDialog) {
        DraggableFloatingDialog(
            onDismiss = { showExitDialog = false },
            borderColor = Color(0xFFA855F7).copy(alpha = 0.45f),
            width = 265.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(NeonLime.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Home,
                        contentDescription = null,
                        tint = NeonLime,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Back to Home?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Do you want to close '${project.name}' and return to the Home screen?",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showExitDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Text("Stay", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = {
                            showExitDialog = false
                            keyboardController?.hide()
                            isKeyboardActiveState = false
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Text("Go Home", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}