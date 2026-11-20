package com.shd.codestudio.editorscreen

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shd.codestudio.FileColor
import com.shd.codestudio.FolderColor
import com.shd.codestudio.NeonLime
import com.shd.codestudio.PurpleHeader
import com.shd.codestudio.RootColor
import com.shd.codestudio.TextSecondary
import com.shd.codestudio.TreeLineColor
import com.shd.codestudio.homescreen.formatSize
import com.shd.codestudio.homescreen.getProjectDirectory
import com.shd.codestudio.models.FileTreeNode
import com.shd.codestudio.models.Project
import com.shd.codestudio.models.SortType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

@Composable
fun DraggableFloatingDialog(
    onDismiss: () -> Unit,
    borderColor: Color = Color.White.copy(alpha = 0.14f),
    width: Dp = 230.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF16161B),
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .width(width)
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .shadow(18.dp, RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(11.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        )
                    }
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DocFilesPage(
    project: Project,
    selectedFile: String,
    expandedFolders: Set<String>,
    androidExpanded: Set<String>,
    treeListState: LazyListState = rememberLazyListState(),
    fontScale: Float,
    refreshKey: Int,
    onFolderToggle: (String) -> Unit,
    onExpandAll: (Set<String>) -> Unit,
    onCollapseAll: () -> Unit,
    onAndroidFolderToggle: (String) -> Unit,
    onAndroidExpandAll: (Set<String>) -> Unit,
    onAndroidCollapseAll: () -> Unit,
    onFileSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    // পারসিস্ট্যান্ট ViewMode, CompactPackages ও PinchZoom স্কেল
    var viewMode by remember { mutableStateOf(loadTreeViewMode(context)) }
    var compactPackages by remember { mutableStateOf(loadCompactPackages(context)) }
    var pinchScale by remember { mutableFloatStateOf(loadTreeFontScale(context)) }

    LaunchedEffect(pinchScale) {
        delay(150)
        saveTreeFontScale(context, pinchScale)
    }

    var sortType by remember { mutableStateOf(loadSortType(context)) }
    var showHidden by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var matchCount by remember { mutableIntStateOf(0) }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedNodes by remember { mutableStateOf(setOf<String>()) }
    var showMultiDeleteDialog by remember { mutableStateOf(false) }

    var clipboardState by remember { mutableStateOf<ClipboardState?>(null) }
    var showFolderPickerForPaste by remember { mutableStateOf(false) }
    var showClipboardItemsPreview by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }

    // ডাউনলোড ফরম্যাট পিকার ও ফাইল ইনফো/নোট স্টেট
    var downloadTargetPaths by remember { mutableStateOf<List<String>?>(null) }
    var infoTargetNode by remember { mutableStateOf<FileTreeNode?>(null) }
    var filesWithAbout by remember(project.name) { mutableStateOf(getFilesWithAbout(context, project.name)) }

    var fileTree by remember { mutableStateOf<List<FileTreeNode>>(emptyList()) }
    var packageName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isPullRefreshing by remember { mutableStateOf(false) }

    var nodeForMenu by remember { mutableStateOf<FileTreeNode?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createParentPath by remember { mutableStateOf("") }
    var createIsFolder by remember { mutableStateOf(false) }
    var showCreatePackageDialog by remember { mutableStateOf(false) }
    var createPackageParentPath by remember { mutableStateOf("") }

    var renameTargetPath by remember { mutableStateOf<String?>(null) }
    var renameTargetName by remember { mutableStateOf("") }
    var deleteTargetPath by remember { mutableStateOf<String?>(null) }
    var deleteTargetName by remember { mutableStateOf("") }
    var imagePreviewPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            delay(100)
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // ১০-১৫ GB প্রজেক্টের জন্য Lazy Directory লোডিং
    LaunchedEffect(
        project.name,
        refreshKey,
        sortType,
        showHidden,
        searchQuery,
        viewMode,
        compactPackages,
        if (viewMode == TreeViewMode.PROJECT && searchQuery.isBlank()) expandedFolders else Unit
    ) {
        if (!isPullRefreshing && fileTree.isEmpty()) isLoading = true
        val (tree, pkg, matches) = withContext(Dispatchers.IO) {
            val (loadedTree, count) = if (searchQuery.isNotBlank()) {
                searchProjectFileTree(context, project.name, sortType, showHidden, searchQuery)
            } else if (viewMode == TreeViewMode.ANDROID) {
                getAndroidFileTree(context, project.name, sortType, compactPackages) to 0
            } else {
                getProjectFileTree(
                    context = context,
                    projectName = project.name,
                    sortType = sortType,
                    showHidden = showHidden,
                    expandedFolders = expandedFolders
                ) to 0
            }
            Triple(loadedTree, getPackageNameFromProject(context, project.name), count)
        }
        fileTree = tree
        packageName = pkg
        matchCount = matches
        filesWithAbout = getFilesWithAbout(context, project.name)
        isLoading = false
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isPullRefreshing,
        onRefresh = {
            isPullRefreshing = true
            onRefresh()
            scope.launch {
                delay(550)
                isPullRefreshing = false
            }
        }
    )

    val rootNode = remember(fileTree, project.name) {
        FileTreeNode(
            name = project.name,
            path = "",
            isDirectory = true,
            children = fileTree.toMutableList()
        )
    }

    val activeExpanded = remember(expandedFolders, androidExpanded, searchQuery, fileTree, viewMode) {
        when {
            searchQuery.isNotBlank() -> getAllFolderPaths(fileTree) + ""
            viewMode == TreeViewMode.ANDROID -> androidExpanded + ""
            else -> expandedFolders + ""
        }
    }

    val flatNodes = remember(rootNode, activeExpanded) {
        flattenFileTree(rootNode, activeExpanded)
    }

    val allSelectablePaths = remember(flatNodes) {
        flatNodes.mapNotNull {
            val p = it.node.path
            if (p.isNotEmpty() && !p.startsWith("__") && p != "app/src/main/manifests") p else null
        }.toSet()
    }

    fun performPasteToFolder(targetFolderPath: String) {
        val state = clipboardState ?: return
        scope.launch(Dispatchers.IO) {
            val count = pasteMultipleItems(
                context = context,
                projectName = project.name,
                sourcePaths = state.paths,
                destFolderPath = targetFolderPath,
                isMove = state.action == ClipboardAction.MOVE
            )
            withContext(Dispatchers.Main) {
                if (count > 0) {
                    val verb = if (state.action == ClipboardAction.MOVE) "Moved" else "Copied"
                    clipboardState = null
                    showFolderPickerForPaste = false
                    onRefresh()
                    Toast.makeText(context, "$verb $count item(s)!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Nothing pasted!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pullRefresh(pullRefreshState)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(12.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(colors = listOf(Color(0xFF242424), Color(0xFF131313))))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ================= হেডার বার =================
                if (isMultiSelectMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1C1C1C))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { isMultiSelectMode = false; selectedNodes = emptySet() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${selectedNodes.size} Selected",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                selectedNodes = if (selectedNodes.size == allSelectablePaths.size && allSelectablePaths.isNotEmpty()) {
                                    emptySet()
                                } else {
                                    allSelectablePaths
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = "Select All", tint = NeonLime, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { if (selectedNodes.isNotEmpty()) showMultiDeleteDialog = true },
                            enabled = selectedNodes.isNotEmpty(),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete Selected",
                                tint = if (selectedNodes.isNotEmpty()) Color(0xFFFF5252) else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else if (isSearchActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.25f))
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { isSearchActive = false; searchQuery = "" },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Search", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(NeonLime),
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Search files & folders...",
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 1
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (searchQuery.isNotBlank()) {
                                        Text(
                                            text = "$matchCount",
                                            color = NeonLime,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .background(NeonLime.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = Color.LightGray,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { searchQuery = "" }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFF141414))
                                .border(1.dp, NeonLime.copy(alpha = 0.65f), RoundedCornerShape(9.dp))
                                .padding(horizontal = 10.dp)
                                .focusRequester(searchFocusRequester)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.25f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(NeonLime, RoundedCornerShape(9.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = project.name.firstOrNull()?.uppercase() ?: "P",
                                color = Color.Black,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = project.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$packageName • ${if (viewMode == TreeViewMode.ANDROID) "Android" else "Project"}",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = { isSearchActive = true }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search", tint = NeonLime, modifier = Modifier.size(20.dp))
                        }

                        Box {
                            IconButton(onClick = { showTopMenu = true }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = NeonLime, modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(
                                expanded = showTopMenu,
                                onDismissRequest = { showTopMenu = false },
                                modifier = Modifier
                                    .background(Color(0xFF1A1A20), RoundedCornerShape(10.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                            ) {
                                Text(
                                    text = "View Mode",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp)
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Android View",
                                            color = if (viewMode == TreeViewMode.ANDROID) NeonLime else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = if (viewMode == TreeViewMode.ANDROID) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Android,
                                            contentDescription = null,
                                            tint = if (viewMode == TreeViewMode.ANDROID) NeonLime else Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        if (viewMode == TreeViewMode.ANDROID) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = NeonLime, modifier = Modifier.size(14.dp))
                                        }
                                    },
                                    onClick = {
                                        viewMode = TreeViewMode.ANDROID
                                        saveTreeViewMode(context, TreeViewMode.ANDROID)
                                        showTopMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Project View",
                                            color = if (viewMode == TreeViewMode.PROJECT) NeonLime else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = if (viewMode == TreeViewMode.PROJECT) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.AccountTree,
                                            contentDescription = null,
                                            tint = if (viewMode == TreeViewMode.PROJECT) NeonLime else Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        if (viewMode == TreeViewMode.PROJECT) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = NeonLime, modifier = Modifier.size(14.dp))
                                        }
                                    },
                                    onClick = {
                                        viewMode = TreeViewMode.PROJECT
                                        saveTreeViewMode(context, TreeViewMode.PROJECT)
                                        showTopMenu = false
                                    }
                                )
                                if (viewMode == TreeViewMode.ANDROID) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Compact Packages",
                                                color = if (compactPackages) NeonLime else Color.White,
                                                fontSize = 11.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.Layers,
                                                contentDescription = null,
                                                tint = if (compactPackages) NeonLime else Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        },
                                        trailingIcon = {
                                            if (compactPackages) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = NeonLime, modifier = Modifier.size(14.dp))
                                            }
                                        },
                                        onClick = {
                                            val next = !compactPackages
                                            compactPackages = next
                                            saveCompactPackages(context, next)
                                            showTopMenu = false
                                        }
                                    )
                                }
                                HorizontalDivider(color = Color.DarkGray)
                                DropdownMenuItem(
                                    text = { Text("Toggle Hidden Files", color = Color.White, fontSize = 11.sp) },
                                    leadingIcon = {
                                        Icon(
                                            if (showHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    },
                                    onClick = { showHidden = !showHidden; showTopMenu = false }
                                )
                                HorizontalDivider(color = Color.DarkGray)
                                DropdownMenuItem(
                                    text = { Text("Expand All", color = Color.White, fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Outlined.UnfoldMore, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp)) },
                                    onClick = {
                                        val all = getAllFolderPaths(fileTree)
                                        if (viewMode == TreeViewMode.ANDROID) onAndroidExpandAll(all) else onExpandAll(all)
                                        showTopMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Collapse All", color = Color.White, fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Outlined.UnfoldLess, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp)) },
                                    onClick = {
                                        if (viewMode == TreeViewMode.ANDROID) onAndroidCollapseAll() else onCollapseAll()
                                        showTopMenu = false
                                    }
                                )
                                HorizontalDivider(color = Color.DarkGray)
                                Text(
                                    text = "Sort by",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp)
                                )
                                SortDropdownItem("Name (A-Z)", sortType == SortType.NAME_AZ) {
                                    sortType = SortType.NAME_AZ; saveSortType(context, sortType); showTopMenu = false
                                }
                                SortDropdownItem("Name (Z-A)", sortType == SortType.NAME_ZA) {
                                    sortType = SortType.NAME_ZA; saveSortType(context, sortType); showTopMenu = false
                                }
                                SortDropdownItem("Newest First", sortType == SortType.DATE_NEWEST) {
                                    sortType = SortType.DATE_NEWEST; saveSortType(context, sortType); showTopMenu = false
                                }
                                SortDropdownItem("Oldest First", sortType == SortType.DATE_OLDEST) {
                                    sortType = SortType.DATE_OLDEST; saveSortType(context, sortType); showTopMenu = false
                                }
                                SortDropdownItem("Custom Order", sortType == SortType.CUSTOM) {
                                    sortType = SortType.CUSTOM; saveSortType(context, sortType); showTopMenu = false
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // ================= ফাইল ট্রি লিস্ট =================
                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonLime, modifier = Modifier.size(28.dp))
                    }
                } else if (searchQuery.isNotBlank() && flatNodes.size <= 1) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No matching files or folders", color = TextSecondary, fontSize = 13.sp)
                    }
                } else {
                    val bottomSpace = if (clipboardState != null || isMultiSelectMode) 82.dp else 24.dp
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        val pressedCount = event.changes.count { it.pressed }
                                        if (pressedCount >= 2) {
                                            val zoom = event.calculateZoom()
                                            if (zoom != 1f) {
                                                pinchScale = (pinchScale * zoom).coerceIn(0.6f, 1.65f)
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                    ) {
                        LazyColumn(
                            state = treeListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 6.dp, bottom = bottomSpace)
                        ) {
                            items(flatNodes, key = { it.node.path.ifEmpty { "__root__" } }) { flat ->
                                val nodePath = flat.node.path
                                val isChecked = selectedNodes.contains(nodePath)
                                val isVirtualFolder = nodePath.startsWith("__") || nodePath == "app/src/main/manifests"

                                IdeTreeRow(
                                    flatNode = flat,
                                    isExpanded = activeExpanded.contains(nodePath),
                                    isSelected = !flat.node.isDirectory && nodePath == selectedFile,
                                    isMultiSelectMode = isMultiSelectMode,
                                    isChecked = isChecked,
                                    hasCustomAbout = filesWithAbout.contains(nodePath),
                                    searchQuery = searchQuery,
                                    fontScale = pinchScale,
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            if (nodePath.isNotEmpty() && !isVirtualFolder) {
                                                selectedNodes = if (isChecked) selectedNodes - nodePath else selectedNodes + nodePath
                                                if (selectedNodes.isEmpty()) isMultiSelectMode = false
                                            }
                                        } else {
                                            if (flat.node.isDirectory) {
                                                if (viewMode == TreeViewMode.ANDROID) {
                                                    onAndroidFolderToggle(nodePath)
                                                } else {
                                                    onFolderToggle(nodePath)
                                                }
                                            } else if (isImageFile(flat.node.name)) {
                                                imagePreviewPath = nodePath
                                            } else {
                                                onFileSelect(nodePath)
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (isMultiSelectMode) {
                                            if (nodePath.isNotEmpty() && !isVirtualFolder) {
                                                selectedNodes = if (isChecked) selectedNodes - nodePath else selectedNodes + nodePath
                                                if (selectedNodes.isEmpty()) isMultiSelectMode = false
                                            }
                                        } else if (!isVirtualFolder) {
                                            nodeForMenu = flat.node
                                        }
                                    },
                                    onInfoClick = {
                                        infoTargetNode = flat.node
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ================= Multi-Select বটম অ্যাকশন বার =================
            if (isMultiSelectMode && selectedNodes.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(10.dp)
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1A20))
                        .border(1.dp, NeonLime.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MultiActionPill(
                        icon = Icons.Outlined.Download,
                        label = "Download",
                        onClick = { downloadTargetPaths = selectedNodes.toList() }
                    )
                    MultiActionPill(
                        icon = Icons.Outlined.ContentCopy,
                        label = "Copy to",
                        onClick = {
                            clipboardState = ClipboardState(selectedNodes.toList(), ClipboardAction.COPY)
                            isMultiSelectMode = false
                            selectedNodes = emptySet()
                        }
                    )
                    MultiActionPill(
                        icon = Icons.Outlined.ContentCut,
                        label = "Move to",
                        onClick = {
                            clipboardState = ClipboardState(selectedNodes.toList(), ClipboardAction.MOVE)
                            isMultiSelectMode = false
                            selectedNodes = emptySet()
                        }
                    )
                }
            }

            // ================= পারসিস্ট্যান্ট Copy / Move পপ-আপ =================
            if (clipboardState != null && !isMultiSelectMode) {
                val clip = clipboardState!!
                val isMove = clip.action == ClipboardAction.MOVE
                val titleText = if (clip.paths.size == 1) {
                    val itemName = File(clip.paths.first()).name
                    if (isMove) "Move: $itemName" else "Copy: $itemName"
                } else {
                    if (isMove) "Moving ${clip.paths.size} items" else "Copying ${clip.paths.size} items"
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .fillMaxWidth()
                        .shadow(10.dp, RoundedCornerShape(11.dp))
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (isMove) Color(0xFF28221A) else Color(0xFF202618))
                        .border(
                            1.dp,
                            if (isMove) Color(0xFFFFB020).copy(alpha = 0.7f) else NeonLime.copy(alpha = 0.65f),
                            RoundedCornerShape(11.dp)
                        )
                        .clickable { showClipboardItemsPreview = true }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isMove) Icons.Outlined.ContentCut else Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            tint = if (isMove) Color(0xFFFFB020) else NeonLime,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = titleText,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Tap Paste or hold folder",
                                color = Color.LightGray.copy(alpha = 0.7f),
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (isMove) Color(0xFFFFB020) else NeonLime)
                                .clickable { showFolderPickerForPaste = true }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text("Paste", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = { clipboardState = null }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.LightGray, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }

        PullRefreshIndicator(
            refreshing = isPullRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            contentColor = NeonLime,
            backgroundColor = Color(0xFF1E1E1E)
        )
    }
    if (infoTargetNode != null) {
        val node = infoTargetNode!!
        var aboutInput by remember(node.path) { mutableStateOf(loadFileAbout(context, project.name, node.path)) }

        DraggableFloatingDialog(
            onDismiss = { infoTargetNode = null },
            borderColor = NeonLime.copy(alpha = 0.45f),
            width = 250.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = NeonLime, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = node.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { infoTargetNode = null }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = node.path,
                color = TextSecondary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1F1F26))
                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Size", color = TextSecondary, fontSize = 10.sp)
                    Text("${formatSize(node.size)} (${node.size} B)", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Last Edit", color = TextSecondary, fontSize = 10.sp)
                    Text(formatLastEditDateTime(node.lastModified), color = NeonLime, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(9.dp))
            Text("Custom About / Notes", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(5.dp))

            BasicTextField(
                value = aboutInput,
                onValueChange = { aboutInput = it },
                maxLines = 4,
                textStyle = TextStyle(color = Color.White, fontSize = 11.sp, lineHeight = 15.sp),
                cursorBrush = SolidColor(NeonLime),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                        if (aboutInput.isEmpty()) {
                            Text(
                                text = "Write details or notes about this file...",
                                color = TextSecondary.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp, max = 90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101014))
                    .border(1.dp, NeonLime.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            )

            Spacer(modifier = Modifier.height(9.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (aboutInput.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFF26262E))
                            .clickable {
                                aboutInput = ""
                                saveFileAbout(context, project.name, node.path, "")
                                filesWithAbout = getFilesWithAbout(context, project.name)
                                Toast.makeText(context, "Note cleared!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Clear", color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(NeonLime)
                        .clickable {
                            saveFileAbout(context, project.name, node.path, aboutInput)
                            filesWithAbout = getFilesWithAbout(context, project.name)
                            infoTargetNode = null
                            Toast.makeText(context, "File note saved!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Save Note", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (downloadTargetPaths != null) {
        val paths = downloadTargetPaths!!
        DraggableFloatingDialog(
            onDismiss = { downloadTargetPaths = null },
            borderColor = NeonLime.copy(alpha = 0.45f),
            width = 230.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Download, contentDescription = null, tint = NeonLime, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (paths.size == 1) "Download Format" else "Export ${paths.size} Items",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { downloadTargetPaths = null }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                DownloadFormat.entries.forEach { format ->
                    val icon = when (format) {
                        DownloadFormat.ZIP -> Icons.Outlined.Archive
                        DownloadFormat.TXT -> Icons.Outlined.Description
                        DownloadFormat.MD -> Icons.AutoMirrored.Outlined.Article
                        DownloadFormat.ORIGINAL -> Icons.AutoMirrored.Outlined.InsertDriveFile
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF22222A))
                            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .clickable {
                                val targets = paths
                                downloadTargetPaths = null
                                scope.launch(Dispatchers.IO) {
                                    if (targets.size == 1) {
                                        val saved = downloadFileToStorageWithFormat(context, project.name, targets.first(), format)
                                        withContext(Dispatchers.Main) {
                                            isMultiSelectMode = false
                                            selectedNodes = emptySet()
                                            if (saved != null) {
                                                Toast.makeText(context, "Saved: $saved", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Download failed!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        val savedCount = downloadMultipleItemsWithFormat(context, project.name, targets, format)
                                        withContext(Dispatchers.Main) {
                                            isMultiSelectMode = false
                                            selectedNodes = emptySet()
                                            if (savedCount > 0) {
                                                val msg = if (format == DownloadFormat.ZIP) {
                                                    "Exported $savedCount items as ZIP!"
                                                } else {
                                                    "Downloaded $savedCount items as ${format.extension.ifEmpty { "Original" }}"
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Download failed!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null, tint = NeonLime, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(format.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    if (showFolderPickerForPaste && clipboardState != null) {
        val allFolders = remember(project.name) {
            listOf("" to "${project.name} (Root)") + getAllProjectFolderPathsBounded(context, project.name, maxFolders = 800)
                .map { it to it }
        }
        val isMove = clipboardState!!.action == ClipboardAction.MOVE

        DraggableFloatingDialog(
            onDismiss = { showFolderPickerForPaste = false },
            borderColor = if (isMove) Color(0xFFFFB020).copy(alpha = 0.5f) else NeonLime.copy(alpha = 0.5f),
            width = 250.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isMove) "Move ${clipboardState!!.paths.size} to..." else "Copy ${clipboardState!!.paths.size} to...",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showFolderPickerForPaste = false }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(allFolders) { (folderPath, displayLabel) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFF22222A))
                            .clickable { performPasteToFolder(folderPath) }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = if (folderPath.isEmpty()) RootColor else FolderColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = displayLabel,
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = "Cancel",
                    color = Color(0xFFFF5252),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { clipboardState = null; showFolderPickerForPaste = false }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }

    if (showClipboardItemsPreview && clipboardState != null) {
        val clip = clipboardState!!
        DraggableFloatingDialog(
            onDismiss = { showClipboardItemsPreview = false },
            width = 235.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (clip.action == ClipboardAction.MOVE) "Move (${clip.paths.size})" else "Copy (${clip.paths.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showClipboardItemsPreview = false }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(5.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(clip.paths) { path ->
                    Text(
                        text = "• $path",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF282830))
                        .clickable { clipboardState = null; showClipboardItemsPreview = false }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Clear", color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(NeonLime)
                        .clickable { showClipboardItemsPreview = false; showFolderPickerForPaste = true }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Paste To...", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (nodeForMenu != null) {
        val node = nodeForMenu!!
        var activeTargetPath by remember(node) { mutableStateOf(node.path) }
        var activeTargetName by remember(node) {
            mutableStateOf(
                if (node.compactedSegments.size > 1) node.compactedSegments.last().first else node.name
            )
        }
        val isPackageTarget = node.isPackage ||
            activeTargetPath.contains("/kotlin") ||
            activeTargetPath.contains("/java")

        DraggableFloatingDialog(
            onDismiss = { nodeForMenu = null },
            width = 220.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (node.compactedSegments.size > 1) activeTargetName else node.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { nodeForMenu = null }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(14.dp))
                }
            }

            if (node.compactedSegments.size > 1) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    node.compactedSegments.forEachIndexed { idx, (segName, segPath) ->
                        if (idx > 0) {
                            Text("›", color = TextSecondary, fontSize = 11.sp)
                        }
                        val isSegSelected = activeTargetPath == segPath
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isSegSelected) NeonLime.copy(alpha = 0.22f) else Color(0xFF24242C))
                                .border(
                                    0.8.dp,
                                    if (isSegSelected) NeonLime else Color.White.copy(alpha = 0.12f),
                                    RoundedCornerShape(5.dp)
                                )
                                .clickable {
                                    activeTargetPath = segPath
                                    activeTargetName = segName
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = segName,
                                color = if (isSegSelected) NeonLime else Color.LightGray,
                                fontSize = 10.sp,
                                fontWeight = if (isSegSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = activeTargetPath,
                    color = TextSecondary,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatSize(node.size), color = TextSecondary, fontSize = 9.sp)
                    if (node.lastModified > 0) {
                        Text(formatLastEditDateTime(node.lastModified), color = TextSecondary, fontSize = 9.sp)
                    }
                }
            }

            if (activeTargetPath.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            isMultiSelectMode = true
                            selectedNodes = setOf(activeTargetPath)
                            nodeForMenu = null
                        }
                        .padding(vertical = 5.dp, horizontal = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = NeonLime, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(7.dp))
                    Text("Select Multiple", color = NeonLime, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 3.dp))

            if (node.isDirectory) {
                ContextMenuRow(Icons.AutoMirrored.Outlined.NoteAdd, "New File") {
                    createParentPath = activeTargetPath
                    createIsFolder = false
                    showCreateDialog = true
                    nodeForMenu = null
                }
                if (isPackageTarget) {
                    ContextMenuRow(Icons.Outlined.Inventory2, "New Package", tintOverride = Color(0xFFFFB74D)) {
                        createPackageParentPath = activeTargetPath
                        showCreatePackageDialog = true
                        nodeForMenu = null
                    }
                }
                ContextMenuRow(Icons.Outlined.CreateNewFolder, "New Folder") {
                    createParentPath = activeTargetPath
                    createIsFolder = true
                    showCreateDialog = true
                    nodeForMenu = null
                }
                if (clipboardState != null) {
                    ContextMenuRow(Icons.Outlined.ContentPaste, "Paste Here", tintOverride = NeonLime) {
                        performPasteToFolder(activeTargetPath)
                        nodeForMenu = null
                    }
                }
                ContextMenuRow(Icons.Outlined.Link, "Copy Path") {
                    copyPathToClipboard(context, project.name, activeTargetPath)
                    Toast.makeText(context, "Copied path!", Toast.LENGTH_SHORT).show()
                    nodeForMenu = null
                }
                if (activeTargetPath.isNotEmpty()) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 3.dp))
                    ContextMenuRow(Icons.Outlined.Download, "Download as...") {
                        downloadTargetPaths = listOf(activeTargetPath)
                        nodeForMenu = null
                    }
                    ContextMenuRow(Icons.Outlined.ContentCopy, "Copy to") {
                        clipboardState = ClipboardState(listOf(activeTargetPath), ClipboardAction.COPY)
                        nodeForMenu = null
                    }
                    ContextMenuRow(Icons.Outlined.ContentCut, "Move to") {
                        clipboardState = ClipboardState(listOf(activeTargetPath), ClipboardAction.MOVE)
                        nodeForMenu = null
                    }
                    ContextMenuRow(Icons.Outlined.Edit, "Rename") {
                        renameTargetPath = activeTargetPath
                        renameTargetName = activeTargetName
                        nodeForMenu = null
                    }
                    ContextMenuRow(Icons.Outlined.Delete, "Delete", isDestructive = true) {
                        deleteTargetPath = activeTargetPath
                        deleteTargetName = activeTargetName
                        nodeForMenu = null
                    }
                }
            } else {
                ContextMenuRow(Icons.Outlined.Info, "File Info & About", tintOverride = NeonLime) {
                    infoTargetNode = node
                    nodeForMenu = null
                }
                ContextMenuRow(Icons.Outlined.ContentCopy, "Copy") {
                    clipboardState = ClipboardState(listOf(node.path), ClipboardAction.COPY)
                    nodeForMenu = null
                }
                ContextMenuRow(Icons.Outlined.ContentCut, "Move to") {
                    clipboardState = ClipboardState(listOf(node.path), ClipboardAction.MOVE)
                    nodeForMenu = null
                }
                ContextMenuRow(Icons.Outlined.Link, "Copy Path") {
                    copyPathToClipboard(context, project.name, node.path)
                    Toast.makeText(context, "Copied path!", Toast.LENGTH_SHORT).show()
                    nodeForMenu = null
                }
                ContextMenuRow(Icons.Outlined.Download, "Download as...") {
                    downloadTargetPaths = listOf(node.path)
                    nodeForMenu = null
                }
                ContextMenuRow(Icons.Outlined.Share, "Share") {
                    shareFile(context, project.name, node.path)
                    nodeForMenu = null
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 3.dp))
                ContextMenuRow(Icons.Outlined.KeyboardArrowUp, "Move Up") {
                    if (moveFileTreeItem(context, project.name, node, -1)) {
                        sortType = SortType.CUSTOM
                        onRefresh()
                    } else {
                        Toast.makeText(context, "Already at top", Toast.LENGTH_SHORT).show()
                    }
                    nodeForMenu = null
                }
                ContextMenuRow(Icons.Outlined.KeyboardArrowDown, "Move Down") {
                    if (moveFileTreeItem(context, project.name, node, 1)) {
                        sortType = SortType.CUSTOM
                        onRefresh()
                    } else {
                        Toast.makeText(context, "Already at bottom", Toast.LENGTH_SHORT).show()
                    }
                    nodeForMenu = null
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 3.dp))
                ContextMenuRow(Icons.Outlined.Edit, "Rename") {
                    renameTargetPath = node.path
                    renameTargetName = node.name
                    nodeForMenu = null
                }
                ContextMenuRow(Icons.Outlined.Delete, "Delete", isDestructive = true) {
                    deleteTargetPath = node.path
                    deleteTargetName = node.name
                    nodeForMenu = null
                }
            }
        }
    }

    if (showCreatePackageDialog) {
        var pkgInput by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }

        DraggableFloatingDialog(
            onDismiss = { showCreatePackageDialog = false },
            borderColor = Color(0xFFFFB74D).copy(alpha = 0.5f),
            width = 240.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Package", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Use dots for nested packages (e.g. ui.home)", color = TextSecondary, fontSize = 9.sp)
            Spacer(modifier = Modifier.height(7.dp))
            BasicTextField(
                value = pkgInput,
                onValueChange = { pkgInput = it; error = "" },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                cursorBrush = SolidColor(NeonLime),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        if (pkgInput.isEmpty()) {
                            Text("e.g. ui.components", color = TextSecondary, fontSize = 11.sp)
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101014))
                    .border(1.dp, Color(0xFFFFB74D).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp)
            )
            if (error.isNotEmpty()) {
                Text(error, color = Color(0xFFFF5252), fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF26262E))
                        .clickable { showCreatePackageDialog = false }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Cancel", color = TextSecondary, fontSize = 11.sp) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(NeonLime)
                        .clickable {
                            val validation = validatePackageNameInput(pkgInput)
                            if (validation != null) {
                                error = validation
                            } else if (createNewPackage(context, project.name, createPackageParentPath, pkgInput)) {
                                showCreatePackageDialog = false
                                onRefresh()
                            } else {
                                error = "Package already exists"
                            }
                        }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Create", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var isFolder by remember { mutableStateOf(createIsFolder) }
        var selectedExt by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        val extensions = listOf(".kt", ".java", ".xml", ".txt", ".json")

        DraggableFloatingDialog(
            onDismiss = { showCreateDialog = false },
            width = 240.dp
        ) {
            Text(if (isFolder) "New Folder" else "New File", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            BasicTextField(
                value = name,
                onValueChange = { name = it; error = "" },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                cursorBrush = SolidColor(NeonLime),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        if (name.isEmpty()) {
                            Text(if (isFolder) "folder_name" else "Example", color = TextSecondary, fontSize = 11.sp)
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101014))
                    .border(1.dp, NeonLime.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp)
            )
            if (!isFolder) {
                Spacer(modifier = Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    extensions.forEach { ext ->
                        val selected = selectedExt == ext
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (selected) NeonLime.copy(alpha = 0.2f) else Color(0xFF26262E))
                                .border(0.8.dp, if (selected) NeonLime else Color.Transparent, RoundedCornerShape(5.dp))
                                .clickable { selectedExt = if (selected) "" else ext }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(ext, color = if (selected) NeonLime else Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (error.isNotEmpty()) {
                Text(error, color = Color(0xFFFF5252), fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF26262E))
                        .clickable { showCreateDialog = false }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Cancel", color = TextSecondary, fontSize = 11.sp) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(NeonLime)
                        .clickable {
                            val trimmed = name.trim()
                            val finalName = if (isFolder || trimmed.endsWith(selectedExt)) trimmed else trimmed + selectedExt
                            val validation = validateFileName(finalName)
                            if (validation != null) {
                                error = validation
                            } else if (createNewItem(context, project.name, createParentPath, finalName, isFolder)) {
                                showCreateDialog = false
                                onRefresh()
                            } else {
                                error = "Already exists"
                            }
                        }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Create", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (renameTargetPath != null) {
        var newName by remember(renameTargetPath) { mutableStateOf(renameTargetName) }
        var error by remember { mutableStateOf("") }

        DraggableFloatingDialog(
            onDismiss = { renameTargetPath = null },
            width = 235.dp
        ) {
            Text("Rename", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            BasicTextField(
                value = newName,
                onValueChange = { newName = it; error = "" },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                cursorBrush = SolidColor(NeonLime),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101014))
                    .border(1.dp, NeonLime.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp)
            )
            if (error.isNotEmpty()) {
                Text(error, color = Color(0xFFFF5252), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF26262E))
                        .clickable { renameTargetPath = null }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Cancel", color = TextSecondary, fontSize = 11.sp) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(NeonLime)
                        .clickable {
                            val trimmed = newName.trim()
                            if (trimmed == renameTargetName) {
                                renameTargetPath = null
                                return@clickable
                            }
                            val validation = validateFileName(trimmed)
                            if (validation != null) {
                                error = validation
                            } else if (renameItem(context, project.name, renameTargetPath!!, trimmed)) {
                                renameTargetPath = null
                                onRefresh()
                            } else {
                                error = "Rename failed"
                            }
                        }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Rename", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (deleteTargetPath != null) {
        DraggableFloatingDialog(
            onDismiss = { deleteTargetPath = null },
            borderColor = Color(0xFFFF5252).copy(alpha = 0.5f),
            width = 215.dp
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.height(6.dp))
                Text("Delete '$deleteTargetName'?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFF282830))
                            .clickable { deleteTargetPath = null }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Cancel", color = Color.White, fontSize = 11.sp) }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFFFF5252))
                            .clickable {
                                deleteItem(context, project.name, deleteTargetPath!!)
                                deleteTargetPath = null
                                onRefresh()
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Delete", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    if (showMultiDeleteDialog) {
        DraggableFloatingDialog(
            onDismiss = { showMultiDeleteDialog = false },
            borderColor = Color(0xFFFF5252).copy(alpha = 0.5f),
            width = 215.dp
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.height(6.dp))
                Text("Delete ${selectedNodes.size} items?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFF282830))
                            .clickable { showMultiDeleteDialog = false }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Cancel", color = Color.White, fontSize = 11.sp) }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFFFF5252))
                            .clickable {
                                deleteMultipleItems(context, project.name, selectedNodes.toList())
                                selectedNodes = emptySet()
                                isMultiSelectMode = false
                                showMultiDeleteDialog = false
                                onRefresh()
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Delete", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    if (imagePreviewPath != null) {
        DraggableFloatingDialog(
            onDismiss = { imagePreviewPath = null },
            width = 260.dp
        ) {
            Text(File(imagePreviewPath!!).name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(8.dp))
            val imgFile = File(getProjectDirectory(context, project.name), imagePreviewPath!!)
            val bmp = remember(imagePreviewPath) { BitmapFactory.decodeFile(imgFile.absolutePath) }
            if (bmp != null) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = "Preview", modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp), contentScale = ContentScale.Fit)
            } else {
                Text("Could not load image", color = Color(0xFFFF5252), fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(7.dp))
                    .background(NeonLime)
                    .clickable { imagePreviewPath = null }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("Close", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MultiActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = NeonLime, modifier = Modifier.size(17.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SortDropdownItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                color = if (isSelected) NeonLime else Color.White,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        },
        trailingIcon = {
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = NeonLime, modifier = Modifier.size(14.dp))
            }
        },
        onClick = onClick
    )
}

@Composable
private fun ContextMenuRow(
    icon: ImageVector,
    label: String,
    isDestructive: Boolean = false,
    tintOverride: Color? = null,
    onClick: () -> Unit
) {
    val itemColor = tintOverride ?: if (isDestructive) Color(0xFFFF5252) else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = itemColor, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = itemColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IdeTreeRow(
    flatNode: FlatFileNode,
    isExpanded: Boolean,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    isChecked: Boolean,
    hasCustomAbout: Boolean,
    searchQuery: String,
    fontScale: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val node = flatNode.node
    val level = flatNode.level
    val isLast = flatNode.isLast
    val parentLines = flatNode.parentLines

    val rowHeight = (31 * fontScale).dp
    val indentWidth = (15 * fontScale).dp
    val iconSize = (14 * fontScale).dp
    val arrowSize = (14 * fontScale).dp

    val textColor = when {
        level == 0 -> RootColor
        node.isPackage -> Color(0xFFFFB74D)
        level == 1 && node.isDirectory -> Color(0xFFFFEB3B)
        node.isDirectory -> FolderColor
        else -> FileColor
    }
    val finalTextColor = if (isSelected) PurpleHeader else textColor
    val (fileIcon, fileIconColor) = when {
        !node.isDirectory -> getIdeFileIconAndColor(node.name)
        node.isPackage -> Icons.Outlined.Inventory2 to textColor
        else -> (if (isExpanded) Icons.Outlined.FolderOpen else Icons.Outlined.Folder) to textColor
    }

    val displayName = remember(node.name, node.compactedSegments, searchQuery, textColor, finalTextColor) {
        val baseColor = if (node.isDirectory) textColor else finalTextColor
        val suffix = when {
            node.name.endsWith(" (androidTest)") -> " (androidTest)"
            node.name.endsWith(" (test)") -> " (test)"
            else -> ""
        }
        if (searchQuery.isBlank()) {
            buildAnnotatedString {
                if (node.compactedSegments.size > 1) {
                    node.compactedSegments.forEachIndexed { idx, (segName, _) ->
                        if (idx > 0) {
                            withStyle(SpanStyle(color = TextSecondary.copy(alpha = 0.7f), fontWeight = FontWeight.Normal)) {
                                append(".")
                            }
                        }
                        withStyle(SpanStyle(color = baseColor)) { append(segName) }
                    }
                    if (suffix.isNotEmpty()) {
                        withStyle(SpanStyle(color = Color(0xFF81C784), fontWeight = FontWeight.Normal)) {
                            append(suffix)
                        }
                    }
                } else {
                    withStyle(SpanStyle(color = baseColor)) { append(node.name) }
                }
            }
        } else {
            val idx = node.name.indexOf(searchQuery, ignoreCase = true)
            if (idx == -1) {
                buildAnnotatedString {
                    withStyle(SpanStyle(color = baseColor)) { append(node.name) }
                }
            } else {
                buildAnnotatedString {
                    withStyle(SpanStyle(color = baseColor)) { append(node.name.substring(0, idx)) }
                    withStyle(SpanStyle(color = Color.Black, background = NeonLime, fontWeight = FontWeight.Bold)) {
                        append(node.name.substring(idx, idx + searchQuery.length))
                    }
                    withStyle(SpanStyle(color = baseColor)) { append(node.name.substring(idx + searchQuery.length)) }
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .background(if (isChecked || isSelected) Color.White.copy(alpha = 0.09f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode && node.path.isNotEmpty() && !node.path.startsWith("__") && node.path != "app/src/main/manifests") {
            val boxSize = (15 * fontScale).dp.coerceAtLeast(11.dp)
            Box(
                modifier = Modifier
                    .padding(start = (8 * fontScale).dp, end = (2 * fontScale).dp)
                    .size(boxSize)
                    .clip(RoundedCornerShape((4 * fontScale).dp))
                    .background(if (isChecked) NeonLime else Color.Transparent)
                    .border(
                        width = 1.4.dp,
                        color = if (isChecked) NeonLime else Color.Gray,
                        shape = RoundedCornerShape((4 * fontScale).dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isChecked) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(boxSize * 0.78f)
                    )
                }
            }
        }

        Row(modifier = Modifier.height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
            parentLines.forEach { hasLine ->
                Box(modifier = Modifier.width(indentWidth).fillMaxHeight()) {
                    if (hasLine) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawLine(
                                color = TreeLineColor.copy(alpha = 0.5f),
                                start = Offset(size.width / 2f, 0f),
                                end = Offset(size.width / 2f, size.height),
                                strokeWidth = 2f
                            )
                        }
                    }
                }
            }
            if (level > 0) {
                Box(modifier = Modifier.width(indentWidth).fillMaxHeight()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        drawLine(color = TreeLineColor.copy(alpha = 0.5f), start = Offset(cx, cy), end = Offset(size.width * 0.85f, cy), strokeWidth = 2f)
                        drawLine(color = TreeLineColor.copy(alpha = 0.5f), start = Offset(cx, 0f), end = Offset(cx, if (isLast) cy else size.height), strokeWidth = 2f)
                    }
                }
            }
        }

        if (node.isDirectory) {
            if (level > 0) {
                Spacer(modifier = Modifier.width((2 * fontScale).dp))
                Box(modifier = Modifier.size(arrowSize), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ArrowDropDown else Icons.AutoMirrored.Filled.ArrowRight,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(arrowSize)
                    )
                }
                Spacer(modifier = Modifier.width((3 * fontScale).dp))
            } else {
                Spacer(modifier = Modifier.width((10 * fontScale).dp))
            }
            Box(modifier = Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                Icon(fileIcon, contentDescription = null, tint = textColor, modifier = Modifier.size(iconSize))
            }
        } else {
            Spacer(modifier = Modifier.width((4 * fontScale).dp + arrowSize))
            Box(modifier = Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                Icon(fileIcon, contentDescription = null, tint = if (isSelected) PurpleHeader else fileIconColor, modifier = Modifier.size(iconSize))
            }
        }

        Spacer(modifier = Modifier.width((6 * fontScale).dp))
        Text(
            text = displayName,
            style = LocalTextStyle.current.copy(
                fontSize = (13 * fontScale).sp,
                lineHeight = (15 * fontScale).sp,
                fontWeight = if (level == 0 || isSelected) FontWeight.Bold else FontWeight.Normal,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (!node.isDirectory && node.path.isNotEmpty()) {
            val infoIconSize = (16 * fontScale).dp.coerceIn(12.dp, 22.dp)
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size((24 * fontScale).dp.coerceIn(20.dp, 30.dp))
                    .clip(CircleShape)
                    .clickable(onClick = onInfoClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "File Information",
                    tint = if (hasCustomAbout) NeonLime else TextSecondary.copy(alpha = 0.75f),
                    modifier = Modifier.size(infoIconSize)
                )
            }
        }
    }
}

private fun getIdeFileIconAndColor(fileName: String): Pair<ImageVector, Color> {
    val cleanName = fileName.substringBefore(" (")
    return when {
        cleanName.endsWith(".kt") -> Icons.Outlined.Code to Color(0xFFA855F7)
        cleanName.endsWith(".java") -> Icons.Outlined.Code to Color(0xFFFF5252)
        cleanName.endsWith(".cpp") || cleanName.endsWith(".h") || cleanName.endsWith(".c") -> Icons.Outlined.Memory to Color(0xFF00E5FF)
        cleanName.endsWith(".xml") || cleanName.endsWith(".html") -> Icons.Outlined.Description to Color(0xFF4CAF50)
        cleanName.contains("gradle") || cleanName.endsWith(".properties") || cleanName.endsWith(".pro") || cleanName.endsWith(".toml") || cleanName == "CMakeLists.txt" -> Icons.Outlined.Settings to Color(0xFFFFEB3B)
        isImageFile(cleanName) -> Icons.Outlined.Image to Color(0xFFFF9800)
        cleanName.endsWith(".json") || cleanName.endsWith(".md") -> Icons.AutoMirrored.Outlined.Article to Color(0xFF03A9F4)
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile to Color.White
    }
}