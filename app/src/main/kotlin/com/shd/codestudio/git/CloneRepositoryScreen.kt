package com.shd.codestudio.git

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.shd.codestudio.DarkBackground
import com.shd.codestudio.NeonLime
import com.shd.codestudio.SurfaceDark
import com.shd.codestudio.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private const val GITHUB_LOGO_PATH_DATA =
    "M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.53 1.032 1.53 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z"

@Composable
fun OfficialGitHubLogo(modifier: Modifier = Modifier, tint: Color = Color.White) {
    val path = remember { PathParser().parsePathString(GITHUB_LOGO_PATH_DATA).toPath() }
    Canvas(modifier = modifier) {
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f
        drawContext.canvas.save()
        drawContext.canvas.scale(scaleX, scaleY)
        drawPath(path = path, color = tint)
        drawContext.canvas.restore()
    }
}

// ==================== কার্ডের একদম ডান প্রান্তের বর্ডারে লাগানো স্ক্রোলবার ====================
private fun Modifier.gitScrollStateBar(
    state: ScrollState,
    width: Dp = 4.dp,
    thumbColor: Color = Color(0xFFD4FF33).copy(alpha = 0.75f),
    trackColor: Color = Color.White.copy(alpha = 0.06f)
): Modifier = drawWithContent {
    drawContent()
    if (state.maxValue > 0) {
        val viewHeight = size.height
        val contentHeight = viewHeight + state.maxValue
        val thumbH = ((viewHeight / contentHeight) * viewHeight).coerceIn(36.dp.toPx(), viewHeight * 0.85f)
        val offsetY = (state.value.toFloat() / state.maxValue.toFloat()) * (viewHeight - thumbH)
        val xOffset = size.width - width.toPx() - 2.dp.toPx()

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(xOffset, 6.dp.toPx()),
            size = Size(width.toPx(), viewHeight - 12.dp.toPx()),
            cornerRadius = CornerRadius(width.toPx() / 2f, width.toPx() / 2f)
        )
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(xOffset, offsetY.coerceIn(6.dp.toPx(), viewHeight - thumbH - 6.dp.toPx())),
            size = Size(width.toPx(), thumbH),
            cornerRadius = CornerRadius(width.toPx() / 2f, width.toPx() / 2f)
        )
    }
}

private fun Modifier.gitLazyListScrollbar(
    state: LazyListState,
    width: Dp = 4.dp,
    thumbColor: Color = Color(0xFFD4FF33).copy(alpha = 0.75f),
    trackColor: Color = Color.White.copy(alpha = 0.06f)
): Modifier = drawWithContent {
    drawContent()
    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    if (totalItems > 0 && visibleItems.isNotEmpty() && totalItems > visibleItems.size) {
        val viewHeight = size.height
        val firstIdx = visibleItems.first().index.toFloat()
        val visibleCount = visibleItems.size.toFloat()
        val thumbH = ((visibleCount / totalItems.toFloat()) * viewHeight).coerceIn(32.dp.toPx(), viewHeight * 0.85f)
        val maxFirstIdx = (totalItems - visibleItems.size).coerceAtLeast(1).toFloat()
        val offsetY = ((firstIdx / maxFirstIdx) * (viewHeight - thumbH)).coerceIn(6.dp.toPx(), viewHeight - thumbH - 6.dp.toPx())
        val xOffset = size.width - width.toPx() - 2.dp.toPx()

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(xOffset, 6.dp.toPx()),
            size = Size(width.toPx(), viewHeight - 12.dp.toPx()),
            cornerRadius = CornerRadius(width.toPx() / 2f, width.toPx() / 2f)
        )
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(xOffset, offsetY),
            size = Size(width.toPx(), thumbH),
            cornerRadius = CornerRadius(width.toPx() / 2f, width.toPx() / 2f)
        )
    }
}

private enum class RepoFilterType { ALL, OWNER, FORKS, PRIVATE }

@Composable
fun CloneRepositoryScreen(
    onBack: () -> Unit,
    onStartClone: (repoUrl: String, folderName: String, branch: String, token: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var credentials by remember { mutableStateOf(GitStorage.loadCredentials(context)) }
    val isLoggedIn = credentials.username.isNotBlank()

    // ইনপুট স্টেট
    var repositoryUrl by remember { mutableStateOf("") }
    var folderName by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("main") }

    // রিপোজিটরি ব্রাউজিং ও ফিল্টার
    var repositories by remember { mutableStateOf<List<GitHubRepoItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var repoFilter by remember { mutableStateOf(RepoFilterType.ALL) }
    var isLoadingRepos by remember { mutableStateOf(false) }
    val repoListState = rememberLazyListState()

    // Create Repository ডায়ালগ স্টেট
    var showCreateRepoDialog by remember { mutableStateOf(false) }

    // ডিলিট রিপোজিটরি ডায়ালগ স্টেট
    var repoToDelete by remember { mutableStateOf<GitHubRepoItem?>(null) }
    var deletePatToken by remember { mutableStateOf(credentials.token) }
    var showDeleteTokenEye by remember { mutableStateOf(false) }
    var isDeletingRepo by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf("") }

    // লগইন ও ইমেইল স্টেট
    var selectedLoginTab by remember { mutableIntStateOf(0) }
    var inputEmailOrUser by remember { mutableStateOf("") }
    var inputToken by remember { mutableStateOf(credentials.token) }
    var showLoginToken by remember { mutableStateOf(false) }
    var isCheckingAccount by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf("") }
    var showHowToCreatePatGuide by remember { mutableStateOf(false) }
    val loginScrollState = rememberScrollState()

    BackHandler { onBack() }

    // স্মার্ট ক্যাশ ফেচিং
    fun refreshUserRepos(token: String, username: String) {
        isLoadingRepos = true
        scope.launch(Dispatchers.IO) {
            val result = GitManager.fetchUserRepositories(token, username)
            withContext(Dispatchers.Main) {
                isLoadingRepos = false
                result.onSuccess { list ->
                    repositories = list
                }.onFailure { err ->
                    Toast.makeText(context, err.message ?: "Failed to load repositories", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun verifyAndSignInWithEmailOrUser(emailOrUsername: String) {
        val clean = emailOrUsername.trim()
        if (clean.isBlank()) {
            loginError = "Please enter your GitHub email or username"
            return
        }
        isCheckingAccount = true
        loginError = ""

        scope.launch(Dispatchers.IO) {
            val result = GitManager.verifyRealGitHubAccount(clean)
            withContext(Dispatchers.Main) {
                isCheckingAccount = false
                result.onSuccess { verifiedCreds ->
                    GitStorage.saveCredentials(context, verifiedCreds)
                    credentials = verifiedCreds
                    Toast.makeText(context, "Connected as ${verifiedCreds.username}!", Toast.LENGTH_SHORT).show()
                    refreshUserRepos(verifiedCreds.token, verifiedCreds.username)
                }.onFailure { err ->
                    loginError = err.message ?: "No GitHub account found for '$clean'"
                }
            }
        }
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && repositories.isEmpty()) {
            refreshUserRepos(credentials.token, credentials.username)
        }
    }

    fun parseRepoDate(dateStr: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            val date = inputFormat.parse(dateStr)
            val outputFormat = SimpleDateFormat("M/d/yy", Locale.US)
            if (date != null) outputFormat.format(date) else ""
        } catch (_: Exception) {
            ""
        }
    }

    val filteredRepos = remember(repositories, searchQuery, repoFilter) {
        repositories.filter { repo ->
            val matchesFilter = when (repoFilter) {
                RepoFilterType.ALL -> true
                RepoFilterType.OWNER -> !repo.isFork
                RepoFilterType.FORKS -> repo.isFork
                RepoFilterType.PRIVATE -> repo.isPrivate
            }
            val matchesSearch = searchQuery.isBlank() ||
                repo.fullName.contains(searchQuery, ignoreCase = true) ||
                (repo.description?.contains(searchQuery, ignoreCase = true) == true) ||
                (repo.language?.contains(searchQuery, ignoreCase = true) == true)
            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .systemBarsPadding()
    ) {
        // ================= ১. টপ হেডার বার =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Clone repository",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            if (isLoggedIn) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceDark)
                        .border(BorderStroke(0.8.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(20.dp))
                        .clickable {
                            GitStorage.clearCredentials(context)
                            credentials = GitCredentials()
                            repositories = emptyList()
                            Toast.makeText(context, "Logged out from GitHub", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = credentials.username,
                        color = NeonLime,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Outlined.Logout, contentDescription = "Log out", tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                }
            }
        }

        // ================= ২. সম্পূর্ণ পেজ Rounded Card-এর ভেতরে =================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                .shadow(12.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(colors = listOf(Color(0xFF1F1F24), Color(0xFF121215))))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(20.dp))
        ) {
            if (!isLoggedIn) {
                // ================= লগইন কার্ড কন্টেন্ট =================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .gitScrollStateBar(loginScrollState)
                        .verticalScroll(loginScrollState)
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E2619))
                            .border(BorderStroke(1.5.dp, NeonLime.copy(alpha = 0.5f)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        OfficialGitHubLogo(modifier = Modifier.size(42.dp), tint = NeonLime)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Connect GitHub Account", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sign in with your GitHub email/username or personal access token.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF111114))
                            .padding(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedLoginTab == 0) NeonLime else Color.Transparent)
                                .clickable { selectedLoginTab = 0; loginError = "" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Email / User",
                                color = if (selectedLoginTab == 0) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedLoginTab == 1) NeonLime else Color.Transparent)
                                .clickable { selectedLoginTab = 1; loginError = "" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Token (PAT)",
                                color = if (selectedLoginTab == 1) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (selectedLoginTab == 0) {
                        OutlinedTextField(
                            value = inputEmailOrUser,
                            onValueChange = { inputEmailOrUser = it; loginError = "" },
                            placeholder = { Text("GitHub email or username", color = TextSecondary.copy(alpha = 0.55f), fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null, tint = NeonLime, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF111114), unfocusedContainerColor = Color(0xFF111114),
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = NeonLime, unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                                cursorColor = NeonLime
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 54.dp)
                                .semantics { contentType = ContentType.EmailAddress },
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { verifyAndSignInWithEmailOrUser(inputEmailOrUser) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            if (isCheckingAccount) {
                                CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                Text("Verify & Sign In", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = inputToken,
                            onValueChange = { inputToken = it; loginError = "" },
                            placeholder = { Text("Paste Token (ghp_xxxxxxxxxxxx)", color = TextSecondary.copy(alpha = 0.55f), fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null, tint = NeonLime, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                IconButton(onClick = { showLoginToken = !showLoginToken }) {
                                    Icon(
                                        imageVector = if (showLoginToken) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                        contentDescription = if (showLoginToken) "Hide token" else "Show token",
                                        tint = if (showLoginToken) NeonLime else TextSecondary
                                    )
                                }
                            },
                            visualTransformation = if (showLoginToken) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF111114), unfocusedContainerColor = Color(0xFF111114),
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = NeonLime, unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                                cursorColor = NeonLime
                            ),
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 54.dp),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                val token = inputToken.trim()
                                if (token.isBlank()) {
                                    loginError = "Please enter your access token"
                                    return@Button
                                }
                                isCheckingAccount = true
                                loginError = ""
                                scope.launch(Dispatchers.IO) {
                                    val res = GitManager.verifyTokenAndGetUser(token)
                                    withContext(Dispatchers.Main) {
                                        isCheckingAccount = false
                                        res.onSuccess { creds ->
                                            GitStorage.saveCredentials(context, creds)
                                            credentials = creds
                                            Toast.makeText(context, "Welcome, ${creds.username}!", Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            loginError = err.message ?: "Invalid Token or Authentication Error"
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            if (isCheckingAccount) {
                                CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                Text("Connect with Token", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    if (loginError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFF5252).copy(alpha = 0.12f))
                                .border(BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.45f)), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Text(loginError, color = Color(0xFFFF8A80), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF141418))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(12.dp))
                            .clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/signup")))
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = NeonLime, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Don't have a GitHub account?", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Create a free account on GitHub in 1 minute", color = TextSecondary, fontSize = 11.sp)
                        }
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF141418))
                            .border(BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.4f)), RoundedCornerShape(12.dp))
                            .clickable {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/settings/tokens/new?scopes=repo,read:user,workflow,delete_repo&description=CodeStudio+Android+IDE")
                                )
                                context.startActivity(intent)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Key, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Generate Token (Auto-Setup)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Pre-selects all required scopes for CodeStudio", color = Color(0xFFC586C0), fontSize = 11.sp)
                        }
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(16.dp))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showHowToCreatePatGuide = !showHowToCreatePatGuide }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.HelpOutline, contentDescription = null, tint = NeonLime, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showHowToCreatePatGuide) "Hide step-by-step guide ▲" else "How to create a Personal Access Token? ▼",
                            color = NeonLime,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    AnimatedVisibility(visible = showHowToCreatePatGuide) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 14.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF101014))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Easy 3-Step Guide for Beginners:", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("1. Tap the purple 'Generate Token (Auto-Setup)' button above.", color = TextSecondary, fontSize = 11.sp)
                            Text("2. Log into GitHub in your browser, scroll to the bottom, and tap 'Generate token'.", color = TextSecondary, fontSize = 11.sp)
                            Text("3. Copy the token starting with 'ghp_' and paste it in the Token tab.", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            } else {
                // ================= ৩. মেইন Clone Repository পেজ (Create Repo বাটন সহ) =================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 14.dp, bottom = 6.dp)
                ) {
                    // ইনপুট সেকশন
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                    ) {
                        CleanCardInputField(
                            value = repositoryUrl,
                            onValueChange = {
                                repositoryUrl = it
                                if (folderName.isBlank()) {
                                    val derived = it.trim().removeSuffix("/").removeSuffix(".git").substringAfterLast("/")
                                    if (derived.isNotBlank() && !derived.contains("github.com")) {
                                        folderName = derived
                                    }
                                }
                            },
                            placeholder = "Repository URL",
                            leadingIcon = Icons.Outlined.Link
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CleanCardInputField(
                                value = folderName,
                                onValueChange = { folderName = it },
                                placeholder = "Folder name",
                                leadingIcon = Icons.Outlined.Folder,
                                modifier = Modifier.weight(1.4f)
                            )

                            CleanCardInputField(
                                value = branchName,
                                onValueChange = { branchName = it },
                                placeholder = "Branch",
                                leadingIcon = null,
                                textColor = NeonLime,
                                modifier = Modifier.weight(0.75f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Clone এবং Create Repo বাটন সারি
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (repositoryUrl.isBlank() || !repositoryUrl.contains("github.com")) {
                                        Toast.makeText(context, "Please enter a valid GitHub URL", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val cleanFolder = folderName.trim().ifEmpty {
                                            repositoryUrl.trim().removeSuffix("/").removeSuffix(".git").substringAfterLast("/")
                                        }
                                        onStartClone(repositoryUrl.trim(), cleanFolder, branchName.trim(), credentials.token)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Clone", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            Button(
                                onClick = { showCreateRepoDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2229), contentColor = Color.White),
                                border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = NeonLime, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Repo", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NeonLime)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // YOUR REPOSITORIES হেডার + Refresh বাটন
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "YOUR REPOSITORIES",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${filteredRepos.size}",
                                color = NeonLime,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { refreshUserRepos(credentials.token, credentials.username) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = NeonLime, modifier = Modifier.size(17.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // ক্লিপিং-মুক্ত সার্চ বার
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF111114))
                                .border(
                                    BorderStroke(1.dp, if (searchQuery.isNotEmpty()) NeonLime.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.1f)),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text("Search repositories", color = TextSecondary.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1)
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                    cursorBrush = SolidColor(NeonLime),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp).clickable { searchQuery = "" }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // ফিল্টার চিপস
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                RepoFilterType.ALL to "All",
                                RepoFilterType.OWNER to "Sources",
                                RepoFilterType.FORKS to "Forks",
                                RepoFilterType.PRIVATE to "Private"
                            ).forEach { (type, label) ->
                                val active = repoFilter == type
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (active) NeonLime else Color(0xFF111114))
                                        .clickable { repoFilter = type }
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (active) Color.Black else Color.LightGray,
                                        fontSize = 11.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Repositories List (কার্ডের একদম প্রান্তে লাগানো স্ক্রোলবার)
                    if (isLoadingRepos) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = NeonLime, strokeWidth = 2.dp)
                        }
                    } else if (filteredRepos.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (searchQuery.isBlank()) "No repositories found" else "No matching repositories",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            state = repoListState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .gitLazyListScrollbar(repoListState),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(start = 14.dp, end = 16.dp, bottom = 14.dp)
                        ) {
                            items(filteredRepos, key = { it.id }) { repo ->
                                val isSelected = repositoryUrl == repo.cloneUrl || repositoryUrl == repo.htmlUrl

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) NeonLime.copy(alpha = 0.08f) else Color.Transparent)
                                        .clickable {
                                            repositoryUrl = repo.cloneUrl
                                            folderName = repo.name
                                            branchName = repo.defaultBranch
                                        }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Folder,
                                        contentDescription = null,
                                        tint = if (isSelected) NeonLime else TextSecondary,
                                        modifier = Modifier.padding(top = 2.dp).size(20.dp)
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = repo.fullName,
                                            color = if (isSelected) NeonLime else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (!repo.description.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = repo.description,
                                                color = TextSecondary,
                                                fontSize = 12.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                lineHeight = 16.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (repo.isFork) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0xFF262626))
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Fork", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                                }
                                            }

                                            if (repo.isPrivate) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0xFFFFB020).copy(alpha = 0.2f))
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Private", color = Color(0xFFFFB020), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            if (!repo.language.isNullOrBlank()) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0xFF262626))
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text(repo.language, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                                }
                                            }

                                            val formattedDate = parseRepoDate(repo.updatedAt)
                                            if (formattedDate.isNotEmpty()) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0xFF262626))
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text(formattedDate, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Normal)
                                                }
                                            }
                                        }
                                    }

                                    // রিপোজিটরি ডিলিট করার ট্র্যাশ আইকন
                                    IconButton(
                                        onClick = {
                                            repoToDelete = repo
                                            deletePatToken = credentials.token
                                            deleteError = ""
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = "Delete Repo",
                                            tint = Color(0xFFFF5252).copy(alpha = 0.75f),
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ================= ৪. Create GitHub Repository ডায়ালগ =================
    if (showCreateRepoDialog) {
        var newRepoName by remember { mutableStateOf("") }
        var newRepoDesc by remember { mutableStateOf("") }
        var isPrivate by remember { mutableStateOf(false) }
        var autoInitReadme by remember { mutableStateOf(true) }
        var patInput by remember { mutableStateOf(credentials.token) }
        var showPatEye by remember { mutableStateOf(false) }
        var isCreating by remember { mutableStateOf(false) }
        var createError by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { if (!isCreating) showCreateRepoDialog = false }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF16161B),
                border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(0.95f)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, tint = NeonLime, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create GitHub Repository", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (!isCreating) showCreateRepoDialog = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = newRepoName,
                        onValueChange = { newRepoName = it; createError = "" },
                        placeholder = { Text("Repository name (e.g. my-android-app)", color = TextSecondary.copy(alpha = 0.5f), fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = NeonLime, unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            cursorColor = NeonLime
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newRepoDesc,
                        onValueChange = { newRepoDesc = it },
                        placeholder = { Text("Description (optional)", color = TextSecondary.copy(alpha = 0.5f), fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = NeonLime, unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            cursorColor = NeonLime
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (credentials.token.isBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = patInput,
                            onValueChange = { patInput = it; createError = "" },
                            placeholder = { Text("GitHub Token (PAT) required to create", color = TextSecondary.copy(alpha = 0.5f), fontSize = 11.sp) },
                            singleLine = true,
                            visualTransformation = if (showPatEye) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPatEye = !showPatEye }) {
                                    Icon(
                                        imageVector = if (showPatEye) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                        contentDescription = if (showPatEye) "Hide token" else "Show token",
                                        tint = if (showPatEye) NeonLime else TextSecondary
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = NeonLime, unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                cursorColor = NeonLime
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (!isPrivate) NeonLime.copy(alpha = 0.18f) else SurfaceDark)
                                .border(BorderStroke(1.dp, if (!isPrivate) NeonLime else Color.White.copy(alpha = 0.1f)), RoundedCornerShape(10.dp))
                                .clickable { isPrivate = false }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Public", color = if (!isPrivate) NeonLime else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isPrivate) Color(0xFFFFB020).copy(alpha = 0.18f) else SurfaceDark)
                                .border(BorderStroke(1.dp, if (isPrivate) Color(0xFFFFB020) else Color.White.copy(alpha = 0.1f)), RoundedCornerShape(10.dp))
                                .clickable { isPrivate = true }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Private", color = if (isPrivate) Color(0xFFFFB020) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { autoInitReadme = !autoInitReadme },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = autoInitReadme,
                            onCheckedChange = { autoInitReadme = it },
                            colors = CheckboxDefaults.colors(checkedColor = NeonLime, checkmarkColor = Color.Black)
                        )
                        Text("Initialize repository with README.md", color = Color.LightGray, fontSize = 12.sp)
                    }

                    if (createError.isNotEmpty()) {
                        Text(createError, color = Color(0xFFFF5252), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val tokenToUse = patInput.trim().ifEmpty { credentials.token }
                            if (newRepoName.isBlank()) {
                                createError = "Repository name is required"
                                return@Button
                            }
                            if (tokenToUse.isBlank()) {
                                createError = "Personal Access Token is required"
                                return@Button
                            }

                            isCreating = true
                            createError = ""
                            scope.launch(Dispatchers.IO) {
                                val res = GitManager.createRepository(
                                    token = tokenToUse,
                                    repoName = newRepoName,
                                    description = newRepoDesc,
                                    isPrivate = isPrivate,
                                    autoInit = autoInitReadme
                                )
                                withContext(Dispatchers.Main) {
                                    isCreating = false
                                    res.onSuccess { createdRepo ->
                                        if (credentials.token != tokenToUse) {
                                            val updatedCreds = credentials.copy(token = tokenToUse)
                                            GitStorage.saveCredentials(context, updatedCreds)
                                            credentials = updatedCreds
                                        }
                                        repositories = listOf(createdRepo) + repositories
                                        repositoryUrl = createdRepo.cloneUrl
                                        folderName = createdRepo.name
                                        branchName = createdRepo.defaultBranch
                                        showCreateRepoDialog = false
                                        Toast.makeText(context, "Repository '${createdRepo.name}' created!", Toast.LENGTH_LONG).show()
                                    }.onFailure { err ->
                                        createError = err.message ?: "Failed to create repository"
                                    }
                                }
                            }
                        },
                        enabled = !isCreating,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Text("Create Repository", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // ================= ৫. GitHub রিপোজিটরি ডিলিট কনফার্মেশন ডায়ালগ =================
    if (repoToDelete != null) {
        val target = repoToDelete!!

        Dialog(onDismissRequest = { if (!isDeletingRepo) repoToDelete = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF16161B),
                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF5252).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Warning, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(28.dp))
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Delete Repository?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Are you sure you want to permanently delete '${target.fullName}' from GitHub? This action cannot be undone.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )

                    if (credentials.token.isBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = deletePatToken,
                            onValueChange = { deletePatToken = it; deleteError = "" },
                            placeholder = { Text("Enter GitHub Token with 'delete_repo'", color = TextSecondary.copy(alpha = 0.5f), fontSize = 11.sp) },
                            singleLine = true,
                            visualTransformation = if (showDeleteTokenEye) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showDeleteTokenEye = !showDeleteTokenEye }) {
                                    Icon(
                                        imageVector = if (showDeleteTokenEye) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                        contentDescription = if (showDeleteTokenEye) "Hide token" else "Show token",
                                        tint = if (showDeleteTokenEye) NeonLime else TextSecondary
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFF5252), unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                cursorColor = Color(0xFFFF5252)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    if (deleteError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(deleteError, color = Color(0xFFFF5252), fontSize = 11.sp, textAlign = TextAlign.Center)
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { repoToDelete = null },
                            enabled = !isDeletingRepo,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26262E), contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Text("Cancel", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                val tokenToUse = deletePatToken.trim().ifEmpty { credentials.token }
                                if (tokenToUse.isBlank()) {
                                    deleteError = "GitHub Token with 'delete_repo' scope is required"
                                    return@Button
                                }
                                val (owner, name) = if (target.fullName.contains("/")) {
                                    target.fullName.substringBefore("/") to target.fullName.substringAfter("/")
                                } else {
                                    credentials.username to target.name
                                }

                                isDeletingRepo = true
                                deleteError = ""
                                scope.launch(Dispatchers.IO) {
                                    val res = GitManager.deleteRepository(tokenToUse, owner, name)
                                    withContext(Dispatchers.Main) {
                                        isDeletingRepo = false
                                        res.onSuccess {
                                            if (credentials.token.isBlank() && tokenToUse.isNotBlank()) {
                                                val updated = credentials.copy(token = tokenToUse)
                                                GitStorage.saveCredentials(context, updated)
                                                credentials = updated
                                            }
                                            repositories = repositories.filter { it.id != target.id }
                                            if (repositoryUrl == target.cloneUrl || repositoryUrl == target.htmlUrl) {
                                                repositoryUrl = ""
                                                folderName = ""
                                            }
                                            repoToDelete = null
                                            Toast.makeText(context, "Repository '${target.name}' deleted from GitHub!", Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            deleteError = err.message ?: "Failed to delete repository"
                                        }
                                    }
                                }
                            },
                            enabled = !isDeletingRepo,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252), contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            if (isDeletingRepo) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                Text("Delete", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanCardInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Color(0xFF111114))
            .border(
                BorderStroke(1.dp, if (value.isNotEmpty()) NeonLime.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f)),
                RoundedCornerShape(11.dp)
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, color = TextSecondary.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(NeonLime),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}