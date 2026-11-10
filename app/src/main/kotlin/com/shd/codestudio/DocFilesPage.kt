package com.shd.codestudio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DocFilesPage(
    project: Project, 
    selectedFile: String,
    onFileSelect: (String) -> Unit
) {
    val context = LocalContext.current
    val fileTree = remember { getProjectFileTree(context, project.name) }
    
    // ফোল্ডার এক্সপ্যান্ড করার স্টেট
    var expandedFolders by remember { mutableStateOf(setOf("app", "app/src", "app/src/main", "app/src/main/kotlin", "app/src/main/res")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(colors = listOf(Color(0xFF2C2C2C), Color(0xFF141414))))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // হেডার
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.2f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).background(NeonLime, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text("M", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(project.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("Project", color = TextSecondary, fontSize = 12.sp)
                    }
                }

                // ফাইল লিস্ট (Tree View)
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("PROJECT FILES", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                    
                    LazyColumn {
                        items(fileTree) { node ->
                            FileTreeItem(
                                node = node,
                                level = 0,
                                expandedFolders = expandedFolders,
                                onFolderToggle = { path ->
                                    expandedFolders = if (expandedFolders.contains(path)) expandedFolders - path else expandedFolders + path
                                },
                                onFileSelect = onFileSelect,
                                selectedFile = selectedFile
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileTreeItem(
    node: FileTreeNode,
    level: Int,
    expandedFolders: Set<String>,
    onFolderToggle: (String) -> Unit,
    onFileSelect: (String) -> Unit,
    selectedFile: String
) {
    val isExpanded = expandedFolders.contains(node.path)
    val isSelected = node.path == selectedFile
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                .clickable { 
                    if (node.isDirectory) onFolderToggle(node.path) else onFileSelect(node.path) 
                }
                .padding(start = (level * 12).dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (node.isDirectory) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = node.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Spacer(modifier = Modifier.width(22.dp)) // অ্যারো এর সমান স্পেস
                // ফাইলের নাম
                Text(
                    text = node.name,
                    color = if (isSelected) NeonLime else TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        // যদি ফোল্ডার এক্সপ্যান্ড করা থাকে, তবে ভেতরের চিলড্রেন দেখাবে
        if (node.isDirectory && isExpanded) {
            node.children.forEach { child ->
                FileTreeItem(
                    node = child,
                    level = level + 1,
                    expandedFolders = expandedFolders,
                    onFolderToggle = onFolderToggle,
                    onFileSelect = onFileSelect,
                    selectedFile = selectedFile
                )
            }
        }
    }
}