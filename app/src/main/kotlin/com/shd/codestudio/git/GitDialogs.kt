package com.shd.codestudio.git

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.shd.codestudio.NeonLime
import com.shd.codestudio.SurfaceDark
import com.shd.codestudio.TextSecondary

@Composable
fun CloneGitRepoDialog(
    initialToken: String,
    onDismiss: () -> Unit,
    onClone: (repoUrl: String, branch: String, token: String) -> Unit
) {
    var repoUrl by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }
    var token by remember { mutableStateOf(initialToken) }
    var showToken by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF141418),
            border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Terminal, contentDescription = null, tint = NeonLime, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clone Git Repository", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))

                Text("Repository URL", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it; error = "" },
                    placeholder = { Text("https://github.com/user/repo", color = TextSecondary.copy(alpha = 0.5f), fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonLime, unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        cursorColor = NeonLime
                    ),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("Branch", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonLime, unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        cursorColor = NeonLime
                    ),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("Personal Access Token (For Private Repos)", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    placeholder = { Text("ghp_xxxx (optional for public)", color = TextSecondary.copy(alpha = 0.5f), fontSize = 11.sp) },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                imageVector = if (showToken) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                contentDescription = if (showToken) "Hide token" else "Show token",
                                tint = if (showToken) NeonLime else TextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonLime, unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        cursorColor = NeonLime
                    ),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp)
                )

                if (error.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error, color = Color(0xFFFF5252), fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = {
                        if (repoUrl.isBlank() || !repoUrl.contains("github.com")) {
                            error = "Enter a valid GitHub URL"
                        } else {
                            onClone(repoUrl.trim(), branch.trim().ifEmpty { "main" }, token.trim())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Clone & Open", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun GitPushDialog(
    projectName: String,
    initialUrl: String,
    initialBranch: String,
    initialToken: String,
    onDismiss: () -> Unit,
    onPush: (repoUrl: String, branch: String, message: String, token: String) -> Unit
) {
    var repoUrl by remember { mutableStateOf(initialUrl) }
    var branch by remember { mutableStateOf(initialBranch.ifEmpty { "main" }) }
    var commitMessage by remember { mutableStateOf("Update from CodeStudio") }
    var token by remember { mutableStateOf(initialToken) }
    var showToken by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF141418),
            border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = NeonLime, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Push '$projectName'", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Text("Remote Repository URL", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(5.dp))
                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it; error = "" },
                    placeholder = { Text("https://github.com/user/repo", color = TextSecondary.copy(alpha = 0.5f), fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonLime, unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        cursorColor = NeonLime
                    ),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("Target Branch", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(5.dp))
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonLime, unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        cursorColor = NeonLime
                    ),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("Commit Message", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(5.dp))
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonLime, unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        cursorColor = NeonLime
                    ),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("GitHub Token (PAT)", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(5.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it; error = "" },
                    placeholder = { Text("ghp_xxxxxxxxxxxx", color = TextSecondary.copy(alpha = 0.5f), fontSize = 11.sp) },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                imageVector = if (showToken) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                contentDescription = if (showToken) "Hide token" else "Show token",
                                tint = if (showToken) NeonLime else TextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonLime, unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        cursorColor = NeonLime
                    ),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp)
                )

                if (error.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error, color = Color(0xFFFF5252), fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (repoUrl.isBlank() || !repoUrl.contains("github.com")) {
                            error = "Enter a valid GitHub URL"
                        } else if (token.isBlank()) {
                            error = "GitHub Personal Access Token is required to push"
                        } else {
                            onPush(repoUrl.trim(), branch.trim().ifEmpty { "main" }, commitMessage.trim(), token.trim())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Commit & Push", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}