package com.shd.codestudio

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// পপ-আপের অ্যানিমেটেড বাটন (হালকা সবুজ কালার)
@Composable
fun AnimatedDialogButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    var isPressed by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> isPressed = true
                is PressInteraction.Release -> isPressed = false
                is PressInteraction.Cancel -> isPressed = false
            }
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "buttonScale"
    )
    
    // নরমাল অবস্থায় হালকা সবুজ (NeonLime এর হালকা রূপ), ক্লিক করলে উজ্জ্বল সবুজ
    val normalColor = Color(0xFFE5FF80) 
    val bgColor = if (isPressed) NeonLime else normalColor
    val txtColor = Color.Black // লেখা সবসময় কালো থাকবে

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(containerColor = bgColor, contentColor = txtColor),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.height(50.dp).scale(scale)
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun ProjectMenuDialog(project: Project, onDismiss: () -> Unit, onDeleteClick: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp), 
            color = Color.Black, // পিওর ব্ল্যাক
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), 
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = project.name, 
                    color = TextPrimary, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 18.sp, 
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                TextButton(
                    onClick = { 
                        val sendIntent = Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, "Check out my project: ${project.name}"); type = "text/plain" }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                        onDismiss()
                    }, 
                    modifier = Modifier.fillMaxWidth()
                ) { 
                    Text("Share Project", color = NeonLime, fontSize = 16.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) 
                }
                
                TextButton(
                    onClick = { onDeleteClick() }, 
                    modifier = Modifier.fillMaxWidth()
                ) { 
                    Text("Delete Project", color = Color(0xFFFF5252), fontSize = 16.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) 
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(projectName: String, onDismiss: () -> Unit, onConfirmDelete: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp), 
            color = Color.Black, 
            border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.3f)), 
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Delete Project?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Are you sure you want to delete $projectName?", color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnimatedDialogButton(text = "Cancel", modifier = Modifier.weight(1f), onClick = onDismiss)
                    AnimatedDialogButton(text = "Delete", modifier = Modifier.weight(1f), onClick = onConfirmDelete)
                }
            }
        }
    }
}

@Composable
fun ImportOptionsDialog(onDismiss: () -> Unit, onPickFolder: () -> Unit, onPickZip: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp), 
            color = Color.Black, 
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), 
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Import Project", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("How do you want to import?", color = TextSecondary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(20.dp))
                
                AnimatedDialogButton(text = "Pick Project Folder", modifier = Modifier.fillMaxWidth(), onClick = onPickFolder)
                Spacer(modifier = Modifier.height(12.dp))
                AnimatedDialogButton(text = "Pick ZIP File", modifier = Modifier.fillMaxWidth(), onClick = onPickZip)
            }
        }
    }
}

@Composable
fun ZipPickerDialog(onDismiss: () -> Unit, onZipSelected: (ZipItem) -> Unit) {
    val context = LocalContext.current
    var zipFiles by remember { mutableStateOf<List<ZipItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    
    var zipSort by remember { mutableStateOf(ZipSort.NEWEST) }
    var zipSizeFilter by remember { mutableStateOf(ZipSizeFilter.ALL) }

    var isRefreshing by remember { mutableStateOf(false) }
    var refreshClicks by remember { mutableStateOf(0) }
    val refreshRotation by animateFloatAsState(
        targetValue = refreshClicks * 360f, 
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600), 
        label = "refreshRot"
    )

    LaunchedEffect(Unit) {
        zipFiles = loadZipFiles(context)
    }

    val filteredZips = remember(zipFiles, searchQuery, zipSort, zipSizeFilter) {
        var list = zipFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
        
        list = when (zipSizeFilter) {
            ZipSizeFilter.ALL -> list
            ZipSizeFilter.SMALL -> list.filter { it.size < 10L * 1024 * 1024 }
            ZipSizeFilter.MEDIUM -> list.filter { it.size in (10L * 1024 * 1024)..(50L * 1024 * 1024) }
            ZipSizeFilter.LARGE -> list.filter { it.size > 50L * 1024 * 1024 }
        }
        
        when (zipSort) {
            ZipSort.NEWEST -> list.sortedByDescending { it.date }
            ZipSort.OLDEST -> list.sortedBy { it.date }
            ZipSort.NAME_AZ -> list.sortedBy { it.name.lowercase() }
            ZipSort.LARGEST -> list.sortedByDescending { it.size }
            ZipSort.SMALLEST -> list.sortedBy { it.size }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp), 
            color = Color.Black, // ব্ল্যাক ব্যাকগ্রাউন্ড
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), 
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.8f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Pick ZIP File", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("${filteredZips.size} zip files found", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                
                Spacer(modifier = Modifier.height(10.dp))
                
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Search zip files...", color = TextSecondary, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color(0xFF121212), unfocusedContainerColor = Color(0xFF121212),
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Button(
                    onClick = { 
                        isRefreshing = true
                        refreshClicks++
                        zipFiles = loadZipFiles(context)
                        isRefreshing = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.rotate(refreshRotation).size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("Sort by", color = TextSecondary, fontSize = 11.sp)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ZipSort.NEWEST to "Newest", ZipSort.OLDEST to "Oldest", ZipSort.NAME_AZ to "Name A-Z", ZipSort.LARGEST to "Largest", ZipSort.SMALLEST to "Smallest").forEach { (sort, label) ->
                        val selected = zipSort == sort
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) NeonLime else Color(0xFF2A2A2A))
                                .clickable { zipSort = sort }
                                .height(24.dp)
                                .padding(horizontal = 8.dp), 
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (selected) Color.Black else Color.White, fontSize = 8.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Text("Filter by size", color = TextSecondary, fontSize = 11.sp)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ZipSizeFilter.ALL to "All", ZipSizeFilter.SMALL to "< 10MB", ZipSizeFilter.MEDIUM to "10-50MB", ZipSizeFilter.LARGE to "> 50MB").forEach { (filter, label) ->
                        val selected = zipSizeFilter == filter
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) NeonLime else Color(0xFF2A2A2A))
                                .clickable { zipSizeFilter = filter }
                                .height(24.dp)
                                .padding(horizontal = 8.dp), 
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (selected) Color.Black else Color.White, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredZips.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(if (isRefreshing) "Scanning..." else "No ZIP files found", color = TextSecondary)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredZips) { z ->
                            val gradientBackground = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFA855F7).copy(alpha = 0.12f),
                                    Color(0xFF161616),
                                    NeonLime.copy(alpha = 0.08f)
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(gradientBackground)
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(10.dp))
                                    .clickable { onZipSelected(z) }
                                    .padding(8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                                    Text("ZIP", color = NeonLime, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        z.name, 
                                        color = Color.White, 
                                        fontSize = 10.sp, 
                                        maxLines = 2, 
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(formatSize(z.size), color = TextSecondary, fontSize = 9.sp)
                                    Text(formatDate(z.date), color = TextSecondary, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel", color = Color(0xFF87CEEB), fontSize = 15.sp)
                }
            }
        }
    }
}