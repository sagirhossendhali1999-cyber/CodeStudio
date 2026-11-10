package com.shd.codestudio

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ==================== ভ্যালিডেশন ফাংশনসমূহ ====================
fun validateProjectName(name: String): String {
    val n = name.trim()
    if (n.isBlank()) return "Project name is required"
    if (n.length > 50) return "Project name is too long (max 50)"
    if (!n[0].isLetter()) return "Must start with a letter (A-Z)"
    for (c in n) {
        if (!(c.isLetterOrDigit() || c == ' ' || c == '_' || c == '-')) return "Only letters, numbers, space, _ and - allowed"
    }
    if (n.contains("  ")) return "Avoid double space"
    return ""
}

fun validatePackageName(pkg: String): String {
    val p = pkg.trim()
    if (p.isBlank()) return "Package name is required"
    if (p.contains(" ")) return "No space allowed - use dots"
    if (!p.contains(".")) return "Must contain at least one dot (e.g. com.example)"
    if (p != p.lowercase()) return "Use only lowercase letters"
    if (p.startsWith(".") || p.endsWith(".")) return "Cannot start or end with a dot"
    if (p.contains("..")) return "Cannot have two dots together"
    
    val keywords = setOf("abstract","assert","boolean","break","byte","case","catch","char","class","const","continue","default","do","double","else","enum","extends","final","finally","float","for","goto","if","implements","import","instanceof","int","interface","long","native","new","package","private","protected","public","return","short","static","strictfp","super","switch","synchronized","this","throw","throws","transient","try","void","volatile","while")
    
    val parts = p.split(".")
    for (part in parts) {
        if (part.isEmpty()) return "Each part cannot be empty"
        if (!part[0].isLetter()) return "Each part must start with a letter"
        if (part in keywords) return "Cannot use Java keyword '$part'"
    }
    return ""
}

@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, packageName: String, language: String, minSdk: Int, targetSdk: Int) -> Unit
) {
    var projectName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("com.example.myproject") }
    var packageEdited by remember { mutableStateOf(false) }
    var showFinalError by remember { mutableStateOf("") }

    var selectedLanguage by remember { mutableStateOf("Kotlin") }
    var selectedMinSdk by remember { mutableStateOf(21) }
    var selectedTargetSdk by remember { mutableStateOf(36) }

    LaunchedEffect(projectName) {
        if (!packageEdited) {
            val cleanName = projectName.lowercase().replace(" ", "").filter { it.isLetterOrDigit() }
            if (cleanName.isNotEmpty() && cleanName[0].isLetter()) {
                packageName = "com.example.$cleanName"
            }
        }
    }

    val nameError = validateProjectName(projectName)
    val pkgError = validatePackageName(packageName)
    val isSdkValid = selectedTargetSdk >= selectedMinSdk

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = Color.Black,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                
                // টপ বার
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(targetValue = if (isPressed) 0.8f else 1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f), label = "backBtnScale")

                    IconButton(onClick = onDismiss, interactionSource = interactionSource, modifier = Modifier.scale(scale)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Create Project", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("New Android project - fill the details", color = TextSecondary, fontSize = 12.sp)
                    }
                }

                // স্ক্রলেবল কন্টেন্ট
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    // Project Name
                    Text("Project name", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it; showFinalError = "" },
                        placeholder = { Text("My Project", color = TextSecondary) },
                        singleLine = true,
                        isError = projectName.isNotBlank() && nameError.isNotEmpty(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark, errorContainerColor = SurfaceDark,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White, errorTextColor = Color.White,
                            focusedBorderColor = if (projectName.isNotBlank() && nameError.isNotEmpty()) Color(0xFFFF5252) else Color.Transparent,
                            unfocusedBorderColor = if (projectName.isNotBlank() && nameError.isNotEmpty()) Color(0xFFFF5252) else Color.Transparent,
                            cursorColor = NeonLime, errorCursorColor = NeonLime
                        ),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (projectName.isNotBlank() && nameError.isNotEmpty()) {
                        Text(nameError, color = Color(0xFFFF5252), fontSize = 11.sp)
                    } else {
                        Text("Start with a letter, e.g. My Project", color = TextSecondary, fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Package Name
                    Text("Package name", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it; packageEdited = true; showFinalError = "" },
                        singleLine = true,
                        isError = pkgError.isNotEmpty(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark, errorContainerColor = SurfaceDark,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White, errorTextColor = Color.White,
                            focusedBorderColor = if (pkgError.isNotEmpty()) Color(0xFFFF5252) else Color.Transparent,
                            unfocusedBorderColor = if (pkgError.isNotEmpty()) Color(0xFFFF5252) else Color.Transparent,
                            cursorColor = NeonLime, errorCursorColor = NeonLime
                        ),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (pkgError.isEmpty() && packageName.isNotEmpty()) {
                        Text("Valid package", color = NeonLime, fontSize = 11.sp)
                    } else if (pkgError.isNotEmpty()) {
                        Text(pkgError, color = Color(0xFFFF5252), fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Language
                    Text("Language", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Kotlin", "Java", "C++").forEach { lang ->
                            val isSelected = selectedLanguage == lang
                            Box(
                                modifier = Modifier
                                    .weight(1f).height(40.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isSelected) NeonLime.copy(alpha = 0.15f) else SurfaceDark)
                                    .then(if (isSelected) Modifier.border(1.dp, NeonLime, RoundedCornerShape(50)) else Modifier.border(1.dp, SurfaceLight, RoundedCornerShape(50)))
                                    .clickable { selectedLanguage = lang },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(lang, color = if (isSelected) NeonLime else Color.White, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        if (selectedLanguage == "Kotlin") "Kotlin + Compose - Recommended" 
                        else if (selectedLanguage == "Java") "Java + XML - Old style" 
                        else "C++ + CMake - Native", 
                        color = TextSecondary, fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Minimum SDK
                    Text("Minimum SDK", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    val minSdks = listOf(21 to "API 21\nAndroid 5.0", 23 to "API 23\nAndroid 6.0", 24 to "API 24\nAndroid 7.0", 26 to "API 26\nAndroid 8.0", 28 to "API 28\nAndroid 9.0", 30 to "API 30\nAndroid 11", 33 to "API 33\nAndroid 13", 34 to "API 34\nAndroid 14", 35 to "API 35\nAndroid 15", 36 to "API 36\nAndroid 16")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        minSdks.chunked(4).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (sdk, label) ->
                                    val isSelected = selectedMinSdk == sdk
                                    Box(
                                        modifier = Modifier
                                            .weight(1f).height(42.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(if (isSelected) NeonLime.copy(alpha = 0.15f) else SurfaceDark)
                                            .then(if (isSelected) Modifier.border(1.dp, NeonLime, RoundedCornerShape(50)) else Modifier.border(1.dp, SurfaceLight, RoundedCornerShape(50)))
                                            .clickable { selectedMinSdk = sdk; showFinalError = "" },
                                        contentAlignment = Alignment.Center
                                    ) { 
                                        Text(label, color = if (isSelected) NeonLime else Color.White, fontSize = 9.sp, lineHeight = 11.sp, textAlign = TextAlign.Center, fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal) 
                                    }
                                }
                                repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Target SDK
                    Text("Target SDK", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    val targetSdks = listOf(30 to "API 30\nAndroid 11", 33 to "API 33\nAndroid 13", 34 to "API 34\nAndroid 14", 35 to "API 35\nAndroid 15", 36 to "API 36\nAndroid 16")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        targetSdks.chunked(4).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (sdk, label) ->
                                    val isSelected = selectedTargetSdk == sdk
                                    val isError = !isSdkValid && isSelected 
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f).height(42.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(if (isSelected) (if(isError) Color(0xFFFF5252).copy(alpha = 0.15f) else NeonLime.copy(alpha = 0.15f)) else SurfaceDark)
                                            .then(
                                                if (isSelected) Modifier.border(1.dp, if(isError) Color(0xFFFF5252) else NeonLime, RoundedCornerShape(50)) 
                                                else Modifier.border(1.dp, SurfaceLight, RoundedCornerShape(50))
                                            )
                                            .clickable { selectedTargetSdk = sdk; showFinalError = "" },
                                        contentAlignment = Alignment.Center
                                    ) { 
                                        Text(label, color = if (isSelected) (if(isError) Color(0xFFFF5252) else NeonLime) else Color.White, fontSize = 9.sp, lineHeight = 11.sp, textAlign = TextAlign.Center, fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal) 
                                    }
                                }
                                repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }

                    if (!isSdkValid) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFFF5252).copy(alpha = 0.1f), RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Target SDK must be greater than or equal to Minimum SDK.", color = Color(0xFFFF5252), fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // বটম ক্রিয়েট বাটন
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (showFinalError.isNotEmpty()) {
                        Text(showFinalError, color = Color(0xFFFF5252), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp), textAlign = TextAlign.Center)
                    }
                    
                    Button(
                        onClick = {
                            if (nameError.isNotEmpty()) showFinalError = nameError
                            else if (pkgError.isNotEmpty()) showFinalError = pkgError
                            else if (!isSdkValid) showFinalError = "Check your SDK selections"
                            else {
                                // সরাসরি onCreate কল হবে
                                onCreate(projectName, packageName, selectedLanguage, selectedMinSdk, selectedTargetSdk)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("Create Project", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}