package com.shd.codestudio.editorscreen

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.shd.codestudio.EditorTextSecondary
import com.shd.codestudio.NeonLime
import com.shd.codestudio.TextSecondary
import com.shd.codestudio.homescreen.getProjectDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun CodePage(
    projectName: String,
    filePath: String,
    allProjectFiles: List<String>,
    editorFontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    isKeyboardActive: Boolean,
    onKeyboardToggle: () -> Unit,
    onFileSelected: (String) -> Unit,
    onBackToFiles: () -> Unit,
    editorController: CodeEditorController = remember { CodeEditorController() }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val fileKey = remember(projectName, filePath) { "$projectName|$filePath" }
    var isLoaded by remember(fileKey) {
        mutableStateOf(filePath.isNotEmpty() && editorController.isSameFileLoaded(fileKey))
    }
    var isDirty by remember { mutableStateOf(false) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var currentLine by remember { mutableLongStateOf(1L) }
    var currentCol by remember { mutableIntStateOf(1) }
    var selectionLength by remember { mutableLongStateOf(0L) }
    var totalLineCount by remember { mutableLongStateOf(1L) }
    var isHugeFileMode by remember { mutableStateOf(false) }
    var isSavingHugeFile by remember { mutableStateOf(false) }
    var diagnosticTick by remember { mutableIntStateOf(0) }

    // ওয়ার্ড র‍্যাপ এবং Go To Line স্টেট
    var isWordWrap by remember { mutableStateOf(false) }
    var showGoToLineDialog by remember { mutableStateOf(false) }

    // সার্চ স্টেট (In File / In Project)
    var showSearchBar by remember { mutableStateOf(false) }
    var isProjectSearchMode by remember { mutableStateOf(false) }
    var showReplaceRow by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var searchMatchCase by remember { mutableStateOf(false) }
    var searchWholeWord by remember { mutableStateOf(false) }
    var searchUseRegex by remember { mutableStateOf(false) }

    var searchMatches by remember { mutableStateOf<List<EditorSearchMatch>>(emptyList()) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }
    var pendingJumpToFirstMatch by remember { mutableStateOf(false) }

    var projectSearchResults by remember { mutableStateOf<List<ProjectSearchResult>>(emptyList()) }
    var projectFileCount by remember { mutableIntStateOf(0) }
    var isSearchingProject by remember { mutableStateOf(false) }
    var projectSearchTick by remember { mutableIntStateOf(0) }
    var pendingProjectJump by remember { mutableStateOf<ProjectSearchResult?>(null) }

    // ফ্লোটিং সিলেকশন মেনু স্টেট
    var showSelectionMenu by remember { mutableStateOf(false) }
    var selectionMenuX by remember { mutableFloatStateOf(0f) }
    var selectionMenuY by remember { mutableFloatStateOf(0f) }
    var hasActiveSelection by remember { mutableStateOf(false) }

    // অটো-কমপ্লিট স্টেট
    var completionPrefix by remember { mutableStateOf("") }
    var completionItems by remember { mutableStateOf<List<CompletionItem>>(emptyList()) }
    var completionX by remember { mutableFloatStateOf(0f) }
    var completionY by remember { mutableFloatStateOf(0f) }

    var editorBoxWidthPx by remember { mutableIntStateOf(0) }
    var editorBoxHeightPx by remember { mutableIntStateOf(0) }

    val diagnosticMessage = remember(diagnosticTick, isLoaded, filePath) {
        if (isLoaded) editorController.getDiagnosticMessage() else ""
    }

    // অ্যাপ ব্যাকগ্রাউন্ড/ক্লোজ হলে ছোট ফাইলের অটো-সেভ
    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) {
            onDispose { editorController.autoSaveIfDirty() }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                    if (editorController.autoSaveIfDirty()) {
                        isDirty = editorController.isDirty()
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                editorController.autoSaveIfDirty()
            }
        }
    }

    fun jumpToMatch(index: Int) {
        if (searchMatches.isEmpty()) return
        val safeIdx = (index + searchMatches.size) % searchMatches.size
        currentMatchIndex = safeIdx
        editorController.jumpToMatch(searchMatches[safeIdx], safeIdx)
    }

    // ব্যাকগ্রাউন্ড প্রজেক্ট-ওয়াইড সার্চ স্ক্যানার
    LaunchedEffect(
        showSearchBar,
        isProjectSearchMode,
        searchQuery,
        searchMatchCase,
        searchWholeWord,
        searchUseRegex,
        allProjectFiles,
        projectSearchTick
    ) {
        if (!showSearchBar || !isProjectSearchMode || searchQuery.isBlank()) {
            projectSearchResults = emptyList()
            projectFileCount = 0
            isSearchingProject = false
            return@LaunchedEffect
        }
        isSearchingProject = true
        delay(200)
        val summary = CodeSearchEngine.searchInProject(
            context = context,
            projectName = projectName,
            allProjectFiles = allProjectFiles,
            query = searchQuery,
            matchCase = searchMatchCase,
            wholeWord = searchWholeWord,
            useRegex = searchUseRegex
        )
        projectSearchResults = summary.results
        projectFileCount = summary.fileCount
        isSearchingProject = false
    }

    // ফাইল লোডিং (৪ MB পর্যন্ত দ্রুত মেমরি মোড, তার চেয়ে বড় বহু GB ও কোটি লাইনের ফাইলে স্ট্রিমিং পেজড মোড)
    LaunchedEffect(projectName, filePath) {
        if (filePath.isEmpty()) {
            editorController.autoSaveIfDirty()
            isLoaded = false
            isHugeFileMode = false
            editorController.loadText("", "// Select a file from DOC Files tab", "", null)
            return@LaunchedEffect
        }
        if (editorController.isSameFileLoaded(fileKey)) {
            isLoaded = true
            editorController.syncUiState()
            if (showSearchBar && !isProjectSearchMode && searchQuery.isNotBlank()) {
                editorController.findMatches(searchQuery, searchMatchCase, searchWholeWord, searchUseRegex)
            }
            pendingProjectJump?.let { target ->
                if (target.filePath == filePath) {
                    editorController.goToLineAndSelect(target.lineNumber.toLong(), target.matchStartCol, target.matchEndCol)
                    pendingProjectJump = null
                }
            }
            return@LaunchedEffect
        }

        editorController.autoSaveIfDirty()
        isLoaded = false

        val targetFile = File(getProjectDirectory(context, projectName), filePath)
        val fileExists = withContext(Dispatchers.IO) { targetFile.exists() && targetFile.isFile }

        if (!fileExists) {
            editorController.loadLines(fileKey, listOf("// File not found: $filePath"), filePath, null)
            isHugeFileMode = false
        } else if (targetFile.length() > 4L * 1024L * 1024L) {
            // ৪ MB-এর বড় যেকোনো ফাইল (৫০ MB, ৫ GB, ১০ GB / কোটি কোটি লাইন) তাৎক্ষণিক স্ট্রিমিং মোডে ওপেন
            isWordWrap = false
            isHugeFileMode = true
            editorController.loadLargeFile(fileKey, filePath, targetFile)
        } else {
            val loadedLines = withContext(Dispatchers.IO) {
                try {
                    val list = ArrayList<String>()
                    BufferedReader(InputStreamReader(FileInputStream(targetFile), Charsets.UTF_8), 32768).use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            list.add(line)
                            line = reader.readLine()
                        }
                    }
                    if (list.isEmpty()) listOf("") else list
                } catch (e: Exception) {
                    listOf("// Error reading file: ${e.message}")
                }
            }
            isHugeFileMode = false
            editorController.loadLines(fileKey, loadedLines, filePath, targetFile)
        }

        isLoaded = true
        isDirty = false
        canUndo = false
        canRedo = false
        showSelectionMenu = false
        completionItems = emptyList()
        diagnosticTick++

        if (showSearchBar && !isProjectSearchMode && searchQuery.isNotBlank()) {
            currentMatchIndex = 0
            editorController.findMatches(searchQuery, searchMatchCase, searchWholeWord, searchUseRegex)
        }
        pendingProjectJump?.let { target ->
            if (target.filePath == filePath) {
                editorController.goToLineAndSelect(target.lineNumber.toLong(), target.matchStartCol, target.matchEndCol)
                pendingProjectJump = null
            }
        }
    }

    // ম্যানুয়াল ফাইল সেভ (বড় ফাইলের জন্য নন-ব্লকিং Zero-Copy ট্রান্সফার)
    fun saveCurrentFile(showToast: Boolean = true) {
        if (!isLoaded || filePath.isEmpty() || isSavingHugeFile) return
        if (isHugeFileMode) isSavingHugeFile = true
        scope.launch(Dispatchers.IO) {
            val targetFile = File(getProjectDirectory(context, projectName), filePath)
            val ok = editorController.saveToFile(targetFile)
            withContext(Dispatchers.Main) {
                isSavingHugeFile = false
                if (ok) {
                    editorController.markSaved()
                    isDirty = false
                    if (showToast) {
                        Toast.makeText(context, "Saved ${filePath.substringAfterLast("/")}", Toast.LENGTH_SHORT).show()
                    }
                } else if (showToast) {
                    Toast.makeText(context, "Failed to save file!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val sortedSmartFiles = remember(filePath, allProjectFiles) {
        if (filePath.isEmpty() || allProjectFiles.isEmpty()) allProjectFiles
        else {
            val currentName = filePath.substringAfterLast("/")
            val baseName = currentName.substringBeforeLast(".")

            fun getRelevanceScore(candidate: String): Int {
                if (candidate == filePath) return 100
                val cName = candidate.substringAfterLast("/")
                val cBase = cName.substringBeforeLast(".")

                return when {
                    cBase.equals(baseName, ignoreCase = true) -> 90
                    cName.contains(baseName, ignoreCase = true) || baseName.contains(cBase, ignoreCase = true) -> 80
                    cName == "AndroidManifest.xml" -> 70
                    cName.endsWith("gradle.kts") || cName.endsWith("gradle") -> 65
                    cName.contains("layout") || cName.contains("values") || cName == "strings.xml" -> 60
                    cName.endsWith(".kt") || cName.endsWith(".java") -> 50
                    else -> 10
                }
            }

            allProjectFiles.sortedWith(
                compareByDescending<String> { getRelevanceScore(it) }
                    .thenBy { it.substringAfterLast("/") }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
    ) {
        // ================= ১. টপ সেকশন =================
        Surface(
            color = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // টুলবার
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF151515))
                        .padding(vertical = 5.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { editorController.undo() },
                        enabled = canUndo,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (canUndo) Color.White else Color.DarkGray, modifier = Modifier.size(19.dp))
                    }

                    IconButton(
                        onClick = { editorController.redo() },
                        enabled = canRedo,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (canRedo) Color.White else Color.DarkGray, modifier = Modifier.size(19.dp))
                    }

                    IconButton(
                        onClick = onKeyboardToggle,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isKeyboardActive) NeonLime.copy(alpha = 0.22f) else Color.Transparent)
                    ) {
                        Icon(imageVector = if (isKeyboardActive) Icons.Default.KeyboardHide else Icons.Default.Keyboard, contentDescription = "Toggle Keyboard", tint = if (isKeyboardActive) NeonLime else Color.LightGray, modifier = Modifier.size(19.dp))
                    }

                    IconButton(
                        onClick = {
                            if (isHugeFileMode || totalLineCount > 200_000L) {
                                isWordWrap = false
                                Toast.makeText(context, "Word wrap disabled for huge files (>200K lines)", Toast.LENGTH_SHORT).show()
                            } else {
                                isWordWrap = !isWordWrap
                                editorController.setWordWrap(isWordWrap)
                                Toast.makeText(context, if (isWordWrap) "Word Wrap: ON" else "Word Wrap: OFF", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isWordWrap) NeonLime.copy(alpha = 0.22f) else Color.Transparent)
                    ) {
                        Icon(Icons.Default.WrapText, contentDescription = "Word Wrap", tint = if (isWordWrap) NeonLime else Color.White, modifier = Modifier.size(19.dp))
                    }

                    IconButton(onClick = { editorController.clearAll() }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear", tint = Color(0xFFFF5252), modifier = Modifier.size(19.dp))
                    }

                    IconButton(
                        onClick = {
                            if (editorController.pasteFromClipboard()) {
                                Toast.makeText(context, "Pasted!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Clipboard is empty!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = Color(0xFF64B5F6), modifier = Modifier.size(19.dp))
                    }

                    IconButton(
                        onClick = {
                            val isSelectionOnly = editorController.copySelectionOrAll()
                            Toast.makeText(context, if (isSelectionOnly) "Selection Copied!" else "Code Copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    IconButton(onClick = { saveCurrentFile(true) }, modifier = Modifier.size(34.dp)) {
                        if (isSavingHugeFile) {
                            CircularProgressIndicator(color = NeonLime, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.Save, contentDescription = "Save", tint = if (isDirty) Color(0xFFFFB020) else NeonLime, modifier = Modifier.size(19.dp))
                        }
                    }

                    IconButton(
                        onClick = {
                            val nextOpen = !showSearchBar
                            showSearchBar = nextOpen
                            if (nextOpen) {
                                val selectedWord = editorController.getSelectedTextOrNull()
                                if (!selectedWord.isNullOrBlank()) {
                                    searchQuery = selectedWord
                                    currentMatchIndex = 0
                                    pendingJumpToFirstMatch = true
                                    if (!isProjectSearchMode) {
                                        editorController.findMatches(selectedWord, searchMatchCase, searchWholeWord, searchUseRegex)
                                    }
                                }
                            } else {
                                showReplaceRow = false
                                searchQuery = ""
                                editorController.findMatches("")
                            }
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (showSearchBar) NeonLime.copy(alpha = 0.22f) else Color.Transparent)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = if (showSearchBar) NeonLime else Color.White, modifier = Modifier.size(19.dp))
                    }
                }

                // প্রফেশনাল সার্চ প্যানেল (ক্লিপ-মুক্ত এবং নন-হ্যাংগিং)
                if (showSearchBar) {
                    CodeSearchUi(
                        isProjectSearchMode = isProjectSearchMode,
                        onSearchModeChange = { isProj ->
                            isProjectSearchMode = isProj
                            if (!isProj && searchQuery.isNotBlank()) {
                                editorController.findMatches(searchQuery, searchMatchCase, searchWholeWord, searchUseRegex)
                            }
                        },
                        searchQuery = searchQuery,
                        onSearchQueryChange = { q ->
                            searchQuery = q
                            currentMatchIndex = 0
                            pendingJumpToFirstMatch = q.isNotBlank()
                            if (!isProjectSearchMode) {
                                editorController.findMatches(q, searchMatchCase, searchWholeWord, searchUseRegex)
                            }
                        },
                        showReplaceRow = showReplaceRow,
                        onToggleReplaceRow = { showReplaceRow = !showReplaceRow },
                        replaceQuery = replaceQuery,
                        onReplaceQueryChange = { replaceQuery = it },
                        matchCase = searchMatchCase,
                        onToggleMatchCase = {
                            searchMatchCase = !searchMatchCase
                            if (!isProjectSearchMode) editorController.findMatches(searchQuery, searchMatchCase, searchWholeWord, searchUseRegex)
                        },
                        wholeWord = searchWholeWord,
                        onToggleWholeWord = {
                            searchWholeWord = !searchWholeWord
                            if (!isProjectSearchMode) editorController.findMatches(searchQuery, searchMatchCase, searchWholeWord, searchUseRegex)
                        },
                        useRegex = searchUseRegex,
                        onToggleUseRegex = {
                            searchUseRegex = !searchUseRegex
                            if (!isProjectSearchMode) editorController.findMatches(searchQuery, searchMatchCase, searchWholeWord, searchUseRegex)
                        },
                        inFileMatchesCount = searchMatches.size,
                        currentInFileIndex = currentMatchIndex,
                        onPrevMatch = { jumpToMatch(currentMatchIndex - 1) },
                        onNextMatch = { jumpToMatch(currentMatchIndex + 1) },
                        onReplaceCurrent = {
                            pendingJumpToFirstMatch = true
                            editorController.replaceCurrentMatch(searchQuery, replaceQuery, searchMatchCase, searchWholeWord, searchUseRegex)
                        },
                        onReplaceAllInFile = {
                            val count = editorController.replaceAllMatches(searchQuery, replaceQuery, searchMatchCase, searchWholeWord, searchUseRegex)
                            Toast.makeText(context, "Replaced $count occurrence(s)", Toast.LENGTH_SHORT).show()
                        },
                        projectSearchResults = projectSearchResults,
                        projectFileCount = projectFileCount,
                        isSearchingProject = isSearchingProject,
                        onSelectProjectResult = { target ->
                            editorController.autoSaveIfDirty()
                            if (target.filePath != filePath) {
                                pendingProjectJump = target
                                onFileSelected(target.filePath)
                            } else {
                                editorController.goToLineAndSelect(target.lineNumber.toLong(), target.matchStartCol, target.matchEndCol)
                            }
                        },
                        onReplaceAllInProject = {
                            scope.launch {
                                editorController.autoSaveIfDirty()
                                val (repCount, fileCount) = CodeSearchEngine.replaceAllInProjectFiles(
                                    context = context,
                                    projectName = projectName,
                                    allProjectFiles = allProjectFiles,
                                    query = searchQuery,
                                    replacement = replaceQuery,
                                    matchCase = searchMatchCase,
                                    wholeWord = searchWholeWord,
                                    useRegex = searchUseRegex
                                )
                                Toast.makeText(context, "Replaced $repCount matches across $fileCount files!", Toast.LENGTH_SHORT).show()
                                projectSearchTick++
                                if (filePath.isNotEmpty()) {
                                    val currentF = File(getProjectDirectory(context, projectName), filePath)
                                    if (currentF.exists() && currentF.length() <= 4L * 1024L * 1024L) {
                                        val text = withContext(Dispatchers.IO) { currentF.readText(Charsets.UTF_8) }
                                        editorController.loadText(fileKey, text, filePath, currentF)
                                    }
                                }
                            }
                        },
                        onClose = {
                            showSearchBar = false
                            showReplaceRow = false
                            searchQuery = ""
                            editorController.findMatches("")
                        }
                    )
                }

                // রাউন্ডেড ফাইল সাজেশন বার
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF181818))
                        .padding(vertical = 4.dp, horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(sortedSmartFiles) { file ->
                        val name = file.substringAfterLast("/")
                        val isSelected = file == filePath
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) NeonLime.copy(alpha = 0.22f) else Color(0xFF282828))
                                .border(
                                    1.dp,
                                    if (isSelected) NeonLime.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.12f),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    editorController.autoSaveIfDirty()
                                    onFileSelected(file)
                                }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name,
                                color = if (isSelected) NeonLime else Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // প্রেজেন্ট ফাইল বার
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A1A))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2D3B23))
                            .clickable {
                                editorController.autoSaveIfDirty()
                                onBackToFiles()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            Icon(Icons.Outlined.Code, contentDescription = null, tint = NeonLime, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = filePath.ifEmpty { "No file selected" },
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    if (isDirty) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFB020))
                        )
                    }

                    val isCodeFile = filePath.endsWith(".kt") || filePath.endsWith(".java") ||
                        filePath.endsWith(".cpp") || filePath.endsWith(".c") ||
                        filePath.endsWith(".h") || filePath.endsWith(".kts") || filePath.endsWith(".gradle")
                    if (isLoaded && !isHugeFileMode && (diagnosticMessage.isNotEmpty() || isCodeFile)) {
                        Spacer(modifier = Modifier.width(6.dp))
                        val hasErr = diagnosticMessage.isNotEmpty()
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (hasErr) Color(0xFFFF5252).copy(alpha = 0.16f) else NeonLime.copy(alpha = 0.12f))
                                .border(
                                    0.8.dp,
                                    if (hasErr) Color(0xFFFF5252).copy(alpha = 0.6f) else NeonLime.copy(alpha = 0.35f),
                                    RoundedCornerShape(5.dp)
                                )
                                .clickable(enabled = hasErr) {
                                    val errLine = diagnosticMessage.removePrefix("Ln ").substringBefore(":").toLongOrNull()
                                    if (errLine != null) editorController.goToLine(errLine)
                                    Toast.makeText(context, diagnosticMessage, Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (hasErr) Icons.Outlined.Warning else Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = if (hasErr) Color(0xFFFF5252) else NeonLime,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (hasErr) diagnosticMessage.substringBefore(":") else "OK",
                                color = if (hasErr) Color(0xFFFF8A80) else NeonLime,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = { editorController.foldAll() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Outlined.UnfoldLess, contentDescription = "Fold All", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    IconButton(onClick = { editorController.unfoldAll() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Outlined.UnfoldMore, contentDescription = "Unfold All", tint = NeonLime, modifier = Modifier.size(16.dp))
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            }
        }

        // ================= ২. Canvas Code Editor =================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged {
                    editorBoxWidthPx = it.width
                    editorBoxHeightPx = it.height
                }
        ) {
            CodeCanvasEditor(
                controller = editorController,
                fontSizeSp = editorFontSize,
                isKeyboardActive = isKeyboardActive,
                isWordWrap = isWordWrap,
                onFontSizeChange = onFontSizeChange,
                onCursorChange = { line, col ->
                    currentLine = line
                    currentCol = col
                },
                onStateChange = { dirty, undoAvail, redoAvail, selLen, lineCnt, hugeMode ->
                    isDirty = isLoaded && dirty
                    canUndo = undoAvail
                    canRedo = redoAvail
                    selectionLength = selLen
                    totalLineCount = lineCnt
                    isHugeFileMode = hugeMode
                    diagnosticTick++
                },
                onSearchResultsChange = { matches ->
                    searchMatches = matches
                    if (matches.isEmpty()) {
                        currentMatchIndex = 0
                        pendingJumpToFirstMatch = false
                    } else if (pendingJumpToFirstMatch) {
                        pendingJumpToFirstMatch = false
                        jumpToMatch(currentMatchIndex.coerceIn(0, matches.lastIndex))
                    } else if (currentMatchIndex >= matches.size) {
                        currentMatchIndex = 0
                    }
                },
                onSelectionMenuChange = { visible, xPx, yPx, hasSel ->
                    showSelectionMenu = visible
                    selectionMenuX = xPx
                    selectionMenuY = yPx
                    hasActiveSelection = hasSel
                },
                onCompletionsChange = { prefix, items, xPx, yPx ->
                    completionPrefix = prefix
                    completionItems = items
                    completionX = xPx
                    completionY = yPx
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 1.dp)
            )

            // ফ্লোটিং সিলেকশন অপশন মেনু
            if (showSelectionMenu && hasActiveSelection && editorBoxWidthPx > 0) {
                val menuWidthPx = with(density) { 276.dp.roundToPx() }
                val menuHeightPx = with(density) { 40.dp.roundToPx() }
                val offsetX = (selectionMenuX - menuWidthPx / 2f).roundToInt()
                    .coerceIn(8, max(8, editorBoxWidthPx - menuWidthPx - 8))
                val aboveY = (selectionMenuY - menuHeightPx - 16).roundToInt()
                val offsetY = if (aboveY >= 8) {
                    aboveY
                } else {
                    (selectionMenuY + with(density) { 36.dp.toPx() }).roundToInt()
                        .coerceIn(8, max(8, editorBoxHeightPx - menuHeightPx - 8))
                }

                Surface(
                    color = Color(0xFF1A1A22),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.75f)),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .offset { IntOffset(offsetX, offsetY) }
                        .height(38.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        SelectionActionChip("Cut", Icons.Default.ContentCut) {
                            editorController.cutSelection()
                            Toast.makeText(context, "Cut!", Toast.LENGTH_SHORT).show()
                        }
                        SelectionDivider()
                        SelectionActionChip("Copy", Icons.Default.ContentCopy) {
                            editorController.copySelectionOrAll()
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        }
                        SelectionDivider()
                        SelectionActionChip("Paste", Icons.Default.ContentPaste) {
                            if (editorController.pasteFromClipboard()) {
                                Toast.makeText(context, "Pasted!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Clipboard is empty!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        SelectionDivider()
                        SelectionActionChip("Select All", Icons.Default.SelectAll) {
                            editorController.selectAll()
                        }
                    }
                }
            }

            // লাইভ অটো-কমপ্লিট পপআপ
            if (isKeyboardActive && completionItems.isNotEmpty() && editorBoxWidthPx > 0) {
                val popupWidthPx = with(density) { 235.dp.roundToPx() }
                val estimatedH = with(density) { minOf(completionItems.size * 34 + 8, 175).dp.roundToPx() }
                val popupX = completionX.roundToInt().coerceIn(8, max(8, editorBoxWidthPx - popupWidthPx - 8))
                val popupY = if (completionY + estimatedH < editorBoxHeightPx) {
                    completionY.roundToInt().coerceAtLeast(8)
                } else {
                    (completionY - estimatedH - with(density) { 28.dp.toPx() }).roundToInt().coerceAtLeast(8)
                }

                Surface(
                    color = Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.55f)),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .offset { IntOffset(popupX, popupY) }
                        .width(235.dp)
                        .heightIn(max = 175.dp)
                ) {
                    LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
                        items(completionItems) { item ->
                            val badgeColor = when (item.kind) {
                                "KW" -> Color(0xFFC586C0)
                                "CL" -> Color(0xFF4EC9B0)
                                "FN" -> Color(0xFFDCDCAA)
                                "SN" -> NeonLime
                                "AT" -> Color(0xFF9CDCFE)
                                else -> Color(0xFF64B5F6)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editorController.applyCompletion(completionPrefix.length, item.insertText)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(badgeColor.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = item.kind,
                                        color = badgeColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.label,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = item.detail,
                                    color = TextSecondary,
                                    fontSize = 9.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // ================= ৩. কুইক সিম্বল ও প্রো শর্টকাট বার =================
        if (isKeyboardActive) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            CodeQuickActionBar(
                onAction = { item ->
                    when (item.actionType) {
                        "NAV_LEFT" -> editorController.moveCursorLeft()
                        "NAV_RIGHT" -> editorController.moveCursorRight()
                        "NAV_UP" -> editorController.moveCursorUp()
                        "NAV_DOWN" -> editorController.moveCursorDown()
                        "NAV_HOME" -> editorController.moveToLineStart()
                        "NAV_END" -> editorController.moveToLineEnd()
                        "FORMAT" -> editorController.formatCode()
                        "COMMENT" -> editorController.toggleComment()
                        "DUP" -> editorController.duplicateLine()
                        "DELETE_LINE" -> editorController.deleteLine()
                        "MOVE_LINE_UP" -> editorController.moveLineUp()
                        "MOVE_LINE_DOWN" -> editorController.moveLineDown()
                        "UNTAB" -> editorController.unindentLine()
                        "INSERT" -> editorController.insertSnippet(item.payload)
                    }
                }
            )
        }

        // ================= ৪. স্ট্যাটাস বার (বড় লাইন ও কলাম সংখ্যাতেও কখনোই কাটবে না) =================
        val langLabel = when {
            filePath.endsWith(".kt") -> "UTF-8 | Kotlin"
            filePath.endsWith(".java") -> "UTF-8 | Java"
            filePath.endsWith(".xml") -> "UTF-8 | XML"
            filePath.endsWith(".cpp") || filePath.endsWith(".h") -> "UTF-8 | C++"
            filePath.endsWith(".kts") || filePath.endsWith(".gradle") -> "UTF-8 | Gradle"
            filePath.endsWith(".json") -> "UTF-8 | JSON"
            else -> "UTF-8 | Text"
        }

        Surface(
            color = Color(0xFF141414),
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // হরাইজন্টাল স্ক্রলযোগ্য কার্ড সারি: কোটি কোটি লাইন ও কলাম হলেও কোনো লেখা কাটবে না
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Line & Column Dedicated Card
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1F2229))
                            .border(0.8.dp, NeonLime.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .clickable { showGoToLineDialog = true }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Ln $currentLine, Col $currentCol",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Text(
                        text = "${editorFontSize.toInt()}sp",
                        color = NeonLime.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .background(NeonLime.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )

                    if (selectionLength > 0L) {
                        Text(
                            text = "Sel $selectionLength",
                            color = Color(0xFF00E5FF),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .background(Color(0xFF00E5FF).copy(alpha = 0.15f), RoundedCornerShape(5.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    if (isHugeFileMode) {
                        Text(
                            text = "STREAM • ${totalLineCount}L",
                            color = Color(0xFFA855F7),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .background(Color(0xFFA855F7).copy(alpha = 0.16f), RoundedCornerShape(5.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    if (!isKeyboardActive) {
                        Text(
                            text = "READ-ONLY",
                            color = Color(0xFFFFB020),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .background(Color(0xFFFFB020).copy(alpha = 0.15f), RoundedCornerShape(5.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    Text(
                        text = langLabel,
                        color = EditorTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // ডানদিকে ফিক্সড Jump Top ও Jump Bottom বাটন
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF262626))
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val downEvent = awaitPointerEvent()
                                    val down = downEvent.changes.firstOrNull { it.pressed } ?: return@awaitEachGesture
                                    var isLongPressing = false
                                    val longPressJob = scope.launch {
                                        delay(280)
                                        isLongPressing = true
                                        while (isActive) {
                                            editorController.scrollByDelta(-60)
                                            delay(16)
                                        }
                                    }

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.none { it.id == down.id && it.pressed }) break
                                    }

                                    longPressJob.cancel()
                                    if (!isLongPressing) {
                                        editorController.jumpToTop()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.KeyboardDoubleArrowUp, contentDescription = "Scroll/Jump Top", tint = NeonLime, modifier = Modifier.size(16.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF262626))
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val downEvent = awaitPointerEvent()
                                    val down = downEvent.changes.firstOrNull { it.pressed } ?: return@awaitEachGesture
                                    var isLongPressing = false
                                    val longPressJob = scope.launch {
                                        delay(280)
                                        isLongPressing = true
                                        while (isActive) {
                                            editorController.scrollByDelta(60)
                                            delay(16)
                                        }
                                    }

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.none { it.id == down.id && it.pressed }) break
                                    }

                                    longPressJob.cancel()
                                    if (!isLongPressing) {
                                        editorController.jumpToBottom()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.KeyboardDoubleArrowDown, contentDescription = "Scroll/Jump Bottom", tint = NeonLime, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    // ================= Go To Line ডায়ালগ =================
    if (showGoToLineDialog) {
        var lineInput by remember { mutableStateOf(currentLine.toString()) }
        DraggableFloatingDialog(
            onDismiss = { showGoToLineDialog = false },
            borderColor = NeonLime.copy(alpha = 0.5f),
            width = 250.dp
        ) {
            Text(text = "Go to Line (1..$totalLineCount)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = lineInput,
                onValueChange = { input -> lineInput = input.filter { it.isDigit() } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonLime,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = NeonLime
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showGoToLineDialog = false }) { Text("Cancel", color = TextSecondary) }
                Button(
                    onClick = {
                        val target = lineInput.toLongOrNull()
                        if (target != null && target in 1L..totalLineCount) {
                            editorController.goToLine(target)
                            showGoToLineDialog = false
                        } else {
                            Toast.makeText(context, "Enter a line between 1 and $totalLineCount", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Go", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SelectionDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(18.dp)
            .background(Color.White.copy(alpha = 0.14f))
    )
}

@Composable
private fun SelectionActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = NeonLime, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}