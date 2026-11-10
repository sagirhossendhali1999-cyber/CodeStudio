package com.shd.codestudio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LivePreviewPage(project: Project, onBackToCode: () -> Unit) {
    var isPhone by remember { mutableStateOf(true) }

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
                // ১. টপ বার
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.2f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Live Preview", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (isPhone) NeonLime else Color(0xFF3A3A3A)).clickable { isPhone = true }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text("Phone", color = if (isPhone) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (!isPhone) NeonLime else Color(0xFF3A3A3A)).clickable { isPhone = false }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text("Tablet", color = if (!isPhone) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = NeonLime, modifier = Modifier.size(24.dp))
                }

                // ২. মোবাইলের মকআপ
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val phoneW = if (isPhone) 260.dp else 360.dp
                    val phoneH = if (isPhone) 480.dp else 380.dp
                    
                    Box(
                        modifier = Modifier
                            .size(width = phoneW, height = phoneH)
                            .background(Color.Black, RoundedCornerShape(32.dp))
                            .border(3.dp, Color(0xFF3A3A3A), RoundedCornerShape(32.dp))
                            .padding(10.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF121212), RoundedCornerShape(24.dp))) {
                            // প্রিভিউ হেডার
                            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(28.dp).background(NeonLime, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                    Text("M", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(project.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                                Text("...", color = Color.Gray, fontSize = 14.sp)
                            }
                            
                            // প্রিভিউ কন্টেন্ট
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Box(modifier = Modifier.size(72.dp).background(NeonLime, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                                    Text("M", color = Color.Black, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(project.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("com.example.myproject", color = Color.Gray, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("App Info - AndroidManifest.xml", color = Color.Gray, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                Box(modifier = Modifier.background(NeonLime, RoundedCornerShape(10.dp)).padding(horizontal = 20.dp, vertical = 8.dp)) {
                                    Text("Open App", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // ৩. বটম বাটন
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp), 
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = { }, colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(44.dp)) {
                        Text("Refresh Preview", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button(onClick = onBackToCode, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = Color.White), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(44.dp)) {
                        Text("Back to Code", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}