package com.shd.codestudio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CodePage(
    selectedFile: String,
    codeText: String,
    onCodeChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 3D শ্যাডো
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                // গ্রেডিয়েন্ট ব্যাকগ্রাউন্ড
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF2C2C2C), Color(0xFF141414))
                    )
                )
                // স্পষ্ট বর্ডার
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ১. টুলবার
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.2f))
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Undo, contentDescription = "Undo", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    Icon(Icons.Default.Redo, contentDescription = "Redo", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    
                    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onSave() }.padding(4.dp)) {
                        Icon(Icons.Default.Save, contentDescription = "Save", tint = NeonLime, modifier = Modifier.size(20.dp))
                    }
                    
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(20.dp))
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                // ২. ফাইল ট্যাব
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF3E4A2E)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("DOC", color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(selectedFile, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ৩. কোড এডিটর
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    val lines = if (codeText.isEmpty()) 1 else codeText.lines().size
                    Column(modifier = Modifier.width(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        for (i in 1..lines) {
                            Text(i.toString(), color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 20.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(end = 4.dp))
                        }
                    }
                    
                    BasicTextField(
                        value = codeText,
                        onValueChange = onCodeChange,
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace, lineHeight = 20.sp),
                        cursorBrush = SolidColor(NeonLime),
                        modifier = Modifier.fillMaxWidth().padding(top = 0.dp)
                    )
                }
            }
        }
    }
}