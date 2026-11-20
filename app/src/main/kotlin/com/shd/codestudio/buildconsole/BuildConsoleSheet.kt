package com.shd.codestudio.buildconsole

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shd.codestudio.NeonLime
import com.shd.codestudio.TextSecondary

@Composable
fun BuildConsoleBottomBar(
    state: BuildConsoleState,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = when (state.status) {
        BuildStatus.IDLE -> Color(0xFFA855F7)
        BuildStatus.BUILDING -> Color(0xFF58A6FF)
        BuildStatus.SUCCESS -> NeonLime
        BuildStatus.FAILED -> Color(0xFFFF5252)
    }

    val headerText = when (state.status) {
        BuildStatus.IDLE -> state.title
        BuildStatus.BUILDING -> state.title.ifBlank { "Building project..." }
        BuildStatus.SUCCESS -> if (state.durationText.isNotEmpty()) {
            "BUILD SUCCESSFUL in ${state.durationText}"
        } else {
            "BUILD SUCCESSFUL"
        }
        BuildStatus.FAILED -> if (state.durationText.isNotEmpty()) {
            "BUILD FAILED in ${state.durationText}"
        } else {
            "BUILD FAILED"
        }
    }

    val subText = when (state.status) {
        BuildStatus.BUILDING -> state.buildLogs.lastOrNull()?.text ?: "Running build tasks..."
        else -> state.subtitle
    }

    var dragAccumulated by remember { mutableFloatStateOf(0f) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF181920),
        border = BorderStroke(
            width = 1.dp,
            color = accentColor.copy(alpha = if (state.status == BuildStatus.IDLE) 0.32f else 0.6f)
        ),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccumulated = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        dragAccumulated += dragAmount
                        if (dragAccumulated < -36f) {
                            dragAccumulated = 0f
                            onExpand()
                        }
                    }
                )
            }
            .clickable { onExpand() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.12f),
                            Color(0xFF15161C),
                            Color(0xFF121318)
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ছোট ড্র্যাগ হ্যান্ডেল পিল
            Box(
                modifier = Modifier
                    .width(30.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f))
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (state.status) {
                    BuildStatus.BUILDING -> {
                        CircularProgressIndicator(
                            color = accentColor,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    BuildStatus.SUCCESS -> {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = NeonLime,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    BuildStatus.FAILED -> {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    BuildStatus.IDLE -> {
                        Icon(
                            imageVector = Icons.Outlined.Terminal,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headerText,
                        color = if (state.status == BuildStatus.IDLE) Color.White else accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subText,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Open Build Console",
                        tint = Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BuildConsoleExpandedSheet(
    visible: Boolean,
    state: BuildConsoleState,
    selectedTab: BuildConsoleTab,
    onSelectTab: (BuildConsoleTab) -> Unit,
    onCollapse: () -> Unit,
    onClearTab: (BuildConsoleTab) -> Unit,
    onRunTerminalCommand: (String) -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onDownloadApk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var logFilter by remember { mutableStateOf("ALL") } // ALL, ERROR, WARN
    var showLogSearch by remember { mutableStateOf(false) }
    var logSearchQuery by remember { mutableStateOf("") }
    var terminalInput by remember { mutableStateOf("") }

    var sheetDragAccumulated by remember { mutableFloatStateOf(0f) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(180)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        ) + fadeOut(tween(150)),
        modifier = modifier
    ) {
        val accentColor = when (state.status) {
            BuildStatus.IDLE -> Color(0xFFA855F7)
            BuildStatus.BUILDING -> Color(0xFF58A6FF)
            BuildStatus.SUCCESS -> NeonLime
            BuildStatus.FAILED -> Color(0xFFFF5252)
        }

        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = Color(0xFF0E0F13),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
            shadowElevation = 24.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ================= ১. ড্র্যাগ হ্যান্ডেল ও স্ট্যাটাস হেডার =================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF15161D))
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = { sheetDragAccumulated = 0f },
                                    onVerticalDrag = { _, dragAmount ->
                                        sheetDragAccumulated += dragAmount
                                        if (sheetDragAccumulated > 55f) {
                                            sheetDragAccumulated = 0f
                                            onCollapse()
                                        }
                                    }
                                )
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f))
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (state.status) {
                                BuildStatus.BUILDING -> {
                                    CircularProgressIndicator(
                                        color = accentColor,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                BuildStatus.SUCCESS -> {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = NeonLime, modifier = Modifier.size(17.dp))
                                }
                                BuildStatus.FAILED -> {
                                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(17.dp))
                                }
                                BuildStatus.IDLE -> {
                                    Icon(Icons.Outlined.Terminal, contentDescription = null, tint = accentColor, modifier = Modifier.size(17.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                val titleDisplay = when (state.status) {
                                    BuildStatus.SUCCESS -> if (state.durationText.isNotEmpty()) {
                                        "BUILD SUCCESSFUL in ${state.durationText}"
                                    } else {
                                        "BUILD SUCCESSFUL"
                                    }
                                    BuildStatus.FAILED -> if (state.durationText.isNotEmpty()) {
                                        "BUILD FAILED in ${state.durationText}"
                                    } else {
                                        "BUILD FAILED"
                                    }
                                    else -> state.title
                                }
                                Text(
                                    text = titleDisplay,
                                    color = if (state.status == BuildStatus.IDLE) Color.White else accentColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (state.status == BuildStatus.BUILDING) "Swipe down to minimize while building" else state.subtitle,
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (selectedTab == BuildConsoleTab.BUILD_OUTPUT || selectedTab == BuildConsoleTab.APP_LOGS) {
                                IconButton(
                                    onClick = { showLogSearch = !showLogSearch },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = "Search Logs",
                                        tint = if (showLogSearch) NeonLime else Color.LightGray,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable { onCollapse() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Minimize",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // GitHub Actions & Download APK অ্যাকশন বার (যখন অ্যাক্টিভ)
                        if (state.actionsUrl.isNotEmpty() || state.completedRunId != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (state.actionsUrl.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.actionsUrl)))
                                        },
                                        border = BorderStroke(1.dp, Color(0xFF58A6FF).copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(9.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.weight(1f).height(34.dp)
                                    ) {
                                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = Color(0xFF58A6FF), modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("Open Actions", color = Color(0xFF58A6FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (state.completedRunId != null) {
                                    Button(
                                        onClick = onDownloadApk,
                                        enabled = !state.isDownloadingApk,
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                                        shape = RoundedCornerShape(9.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.weight(1f).height(34.dp)
                                    ) {
                                        if (state.isDownloadingApk) {
                                            CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                        } else {
                                            Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("Download APK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    // ================= ২. প্রো IDE ট্যাব বার =================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF121319))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BuildConsoleTab.entries.forEach { tab ->
                            val isSelected = selectedTab == tab
                            val countBadge = when (tab) {
                                BuildConsoleTab.BUILD_OUTPUT -> state.buildLogs.size
                                BuildConsoleTab.APP_LOGS -> state.appLogs.size
                                BuildConsoleTab.TERMINAL -> 0
                                BuildConsoleTab.DIAGNOSTICS -> state.diagnostics.size
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(
                                        if (isSelected) NeonLime.copy(alpha = 0.16f) else Color.Transparent
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) NeonLime.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.07f),
                                        shape = RoundedCornerShape(9.dp)
                                    )
                                    .clickable { onSelectTab(tab) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tab.title,
                                    color = if (isSelected) NeonLime else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                if (countBadge > 0) {
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(
                                                if (tab == BuildConsoleTab.DIAGNOSTICS && state.diagnostics.any { it.isError }) {
                                                    Color(0xFFFF5252).copy(alpha = 0.25f)
                                                } else if (isSelected) {
                                                    NeonLime.copy(alpha = 0.22f)
                                                } else {
                                                    Color.White.copy(alpha = 0.1f)
                                                }
                                            )
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = countBadge.toString(),
                                            color = if (tab == BuildConsoleTab.DIAGNOSTICS && state.diagnostics.any { it.isError }) {
                                                Color(0xFFFF8A80)
                                            } else if (isSelected) NeonLime else Color.LightGray,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ================= ৩. লগ সার্চ ও সিভিয়ারিটি ফিল্টার বার =================
                    if ((selectedTab == BuildConsoleTab.BUILD_OUTPUT || selectedTab == BuildConsoleTab.APP_LOGS) && showLogSearch) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF14151B))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0A0B0E))
                                    .border(1.dp, NeonLime.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    if (logSearchQuery.isEmpty()) {
                                        Text("Filter logs...", color = TextSecondary, fontSize = 11.sp)
                                    }
                                    BasicTextField(
                                        value = logSearchQuery,
                                        onValueChange = { logSearchQuery = it },
                                        singleLine = true,
                                        textStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                        cursorBrush = SolidColor(NeonLime),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (logSearchQuery.isNotEmpty()) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(14.dp).clickable { logSearchQuery = "" }
                                    )
                                }
                            }

                            listOf("ALL" to "All", "ERROR" to "Err", "WARN" to "Warn").forEach { (key, label) ->
                                val active = logFilter == key
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(if (active) NeonLime else Color(0xFF22232B))
                                        .clickable { logFilter = key }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (active) Color.Black else Color.LightGray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                    // ================= ৪. কনসোল বডি =================
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF08090C))
                    ) {
                        when (selectedTab) {
                            BuildConsoleTab.BUILD_OUTPUT, BuildConsoleTab.APP_LOGS -> {
                                val rawList = if (selectedTab == BuildConsoleTab.BUILD_OUTPUT) state.buildLogs else state.appLogs
                                val filteredList = remember(rawList, logFilter, logSearchQuery) {
                                    rawList.filter { entry ->
                                        val matchesFilter = when (logFilter) {
                                            "ERROR" -> entry.severity == LogSeverity.ERROR
                                            "WARN" -> entry.severity == LogSeverity.WARNING || entry.severity == LogSeverity.ERROR
                                            else -> true
                                        }
                                        val matchesQuery = logSearchQuery.isBlank() ||
                                            entry.text.contains(logSearchQuery, ignoreCase = true)
                                        matchesFilter && matchesQuery
                                    }
                                }

                                if (filteredList.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = if (selectedTab == BuildConsoleTab.BUILD_OUTPUT) {
                                                "No build output yet.\nTap 'Build with GitHub' or 'Build Local Engine' to start."
                                            } else {
                                                "No app logs recorded yet."
                                            },
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                } else {
                                    NumberedLogViewer(
                                        lines = filteredList,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            BuildConsoleTab.TERMINAL -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    NumberedLogViewer(
                                        lines = state.terminalLines,
                                        modifier = Modifier.weight(1f).fillMaxWidth()
                                    )

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                                    // কুইক কমান্ড পিল
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF101116))
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf("help", "status", "files", "check", "clear").forEach { cmd ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF1E2028))
                                                    .border(0.6.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                                    .clickable { onRunTerminalCommand(cmd) }
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = cmd,
                                                    color = NeonLime,
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }

                                    // কমান্ড ইনপুট বক্স
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF13141A))
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "$",
                                            color = NeonLime,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                            if (terminalInput.isEmpty()) {
                                                Text(
                                                    text = "Enter command (e.g. help, status, files)...",
                                                    color = TextSecondary.copy(alpha = 0.6f),
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            BasicTextField(
                                                value = terminalInput,
                                                onValueChange = { terminalInput = it },
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Monospace
                                                ),
                                                cursorBrush = SolidColor(NeonLime),
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                                keyboardActions = KeyboardActions(
                                                    onSend = {
                                                        val cmd = terminalInput.trim()
                                                        if (cmd.isNotEmpty()) {
                                                            onRunTerminalCommand(cmd)
                                                            terminalInput = ""
                                                        }
                                                    }
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(NeonLime)
                                                .clickable {
                                                    val cmd = terminalInput.trim()
                                                    if (cmd.isNotEmpty()) {
                                                        onRunTerminalCommand(cmd)
                                                        terminalInput = ""
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Run", tint = Color.Black, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }

                            BuildConsoleTab.DIAGNOSTICS -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF101116))
                                            .padding(horizontal = 12.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val errCount = state.diagnostics.count { it.isError }
                                        val warnCount = state.diagnostics.count { !it.isError }
                                        Text(
                                            text = "$errCount error(s), $warnCount warning(s)",
                                            color = if (errCount > 0) Color(0xFFFF8A80) else NeonLime,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF1F212B))
                                                .clickable { onRefreshDiagnostics() }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Outlined.Refresh, contentDescription = null, tint = NeonLime, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Re-scan", color = NeonLime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (state.diagnostics.isEmpty()) {
                                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = NeonLime, modifier = Modifier.size(28.dp))
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text("No issues detected in project!", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text("Manifest, Gradle & source checks passed.", color = TextSecondary, fontSize = 10.sp)
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            contentPadding = PaddingValues(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            itemsIndexed(state.diagnostics) { _, issue ->
                                                val issueColor = if (issue.isError) Color(0xFFFF5252) else Color(0xFFFFB020)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(9.dp))
                                                        .background(Color(0xFF14151B))
                                                        .border(0.8.dp, issueColor.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                                                        .padding(10.dp),
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    Icon(
                                                        imageVector = if (issue.isError) Icons.Outlined.ErrorOutline else Icons.Outlined.Warning,
                                                        contentDescription = null,
                                                        tint = issueColor,
                                                        modifier = Modifier.padding(top = 1.dp).size(15.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "${issue.filePath}${if (issue.line > 0) ":${issue.line}" else ""}",
                                                            color = Color(0xFF79C0FF),
                                                            fontSize = 10.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = issue.message,
                                                            color = Color(0xFFE5E5E5),
                                                            fontSize = 11.sp,
                                                            lineHeight = 15.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ================= ৫. ফ্লোটিং Clear / Copy / Share বাটনসমূহ (ডান নিচে) =================
                        if (selectedTab != BuildConsoleTab.TERMINAL && selectedTab != BuildConsoleTab.DIAGNOSTICS) {
                            val activeLines = if (selectedTab == BuildConsoleTab.BUILD_OUTPUT) state.buildLogs else state.appLogs
                            if (activeLines.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Clear বাটন
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF1E2028),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                                        shadowElevation = 6.dp,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clickable { onClearTab(selectedTab) }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear", tint = Color(0xFFFF8A80), modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    // Copy বাটন
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF1E2028),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                                        shadowElevation = 6.dp,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clickable {
                                                val txt = activeLines.joinToString("\n") { "[${it.timestamp}] ${it.text}" }
                                                clipboard.setText(AnnotatedString(txt))
                                                Toast.makeText(context, "Logs copied!", Toast.LENGTH_SHORT).show()
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = Color.White, modifier = Modifier.size(17.dp))
                                        }
                                    }

                                    // Share বাটন (হাইলাইটেড NeonLime FAB)
                                    Surface(
                                        shape = CircleShape,
                                        color = NeonLime,
                                        shadowElevation = 8.dp,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clickable {
                                                val txt = activeLines.joinToString("\n") { "[${it.timestamp}] ${it.text}" }
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, "CodeStudio ${selectedTab.title}")
                                                    putExtra(Intent.EXTRA_TEXT, txt)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share ${selectedTab.title}"))
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Outlined.Share, contentDescription = "Share", tint = Color.Black, modifier = Modifier.size(19.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberedLogViewer(
    lines: List<BuildLogLine>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    Box(
        modifier = modifier
            .horizontalScroll(horizontalScrollState)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(min = 640.dp)
                .fillMaxHeight()
                .padding(vertical = 8.dp),
            contentPadding = PaddingValues(bottom = 64.dp)
        ) {
            itemsIndexed(lines, key = { idx, item -> "${item.id}_$idx" }) { idx, entry ->
                val textColor = when (entry.severity) {
                    LogSeverity.SUCCESS -> NeonLime
                    LogSeverity.ERROR -> Color(0xFFFF5252)
                    LogSeverity.WARNING -> Color(0xFFFFB020)
                    LogSeverity.RUNNER -> Color(0xFF58A6FF)
                    LogSeverity.TASK -> Color(0xFFC586C0)
                    LogSeverity.INFO -> Color(0xFFE0E0E6)
                }
                val isBold = entry.severity == LogSeverity.SUCCESS || entry.severity == LogSeverity.ERROR

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "${idx + 1}".padStart(3, ' '),
                        color = Color(0xFF555764),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "[${entry.timestamp}]",
                        color = Color(0xFF5C7080),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = entry.text,
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false
                    )
                }
            }
        }
    }
}