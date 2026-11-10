package com.shd.codestudio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    project: Project,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val scope = rememberCoroutineScope()

    // প্রজেক্টের প্রথম ফাইলটি ডিফল্ট হিসেবে সিলেক্ট থাকবে
    var selectedFile by remember { mutableStateOf("AndroidManifest.xml") }
    var codeText by remember { mutableStateOf("") }

    // ফাইল সিলেক্ট হলে কোড লোড হবে
    LaunchedEffect(selectedFile, project.name) {
        codeText = readFileContent(context, project.name, selectedFile)
    }

    // কারেন্ট ফাইল সেভ করার হেল্পার ফাংশন
    fun saveCurrentFile() {
        if (selectedFile.isNotBlank()) {
            saveFileContent(context, project.name, selectedFile, codeText)
        }
    }

    // ফোনের ব্যাক বাটন প্রেস করলে আগে সেভ হবে, তারপর হোমে যাবে
    BackHandler {
        saveCurrentFile()
        onBack()
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).systemBarsPadding()) {
        
        // ================= ট্যাব =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E1E))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf("DOC Files", "</> Code", "Live Preview")
            tabs.forEachIndexed { index, title ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) NeonLime else Color.Transparent)
                        .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) Color.Black else TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // ================= পেজগুলো =================
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> DocFilesPage(
                    project = project, 
                    selectedFile = selectedFile,
                    onFileSelect = { newFile -> 
                        // নতুন ফাইল সিলেক্ট করার আগে, বর্তমানে ওপেন থাকা ফাইলটি সেভ করে নেবে
                        if (selectedFile != newFile) {
                            saveCurrentFile()
                        }
                        selectedFile = newFile
                        scope.launch { pagerState.animateScrollToPage(1) }
                    }
                )
                1 -> CodePage(
                    selectedFile = selectedFile,
                    codeText = codeText,
                    onCodeChange = { codeText = it },
                    onSave = { 
                        saveCurrentFile()
                        android.widget.Toast.makeText(context, "Saved $selectedFile", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
                2 -> LivePreviewPage(project, onBackToCode = { scope.launch { pagerState.animateScrollToPage(1) } })
            }
        }
    }
}