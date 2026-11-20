package com.shd.codestudio.nativeengine

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shd.codestudio.NeonLime
import com.shd.codestudio.SurfaceDark
import com.shd.codestudio.TextSecondary
import com.shd.codestudio.homescreen.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun EngineManagerDialog(
    recommendedApi: Int = 36,
    onDismiss: () -> Unit,
    onReadyToBuild: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var aapt2Status by remember { mutableStateOf<Aapt2HealthStatus?>(null) }
    var kotlincStatus by remember { mutableStateOf<KotlincPackStatus?>(null) }
    var installedApis by remember { mutableStateOf<List<Int>>(emptyList()) }
    var savedKeystore by remember { mutableStateOf<CustomKeystoreConfig?>(null) }
    var cacheStats by remember { mutableStateOf(EngineCacheStats(0L, 0L, 0L, 0L)) }
    var refreshTick by remember { mutableIntStateOf(0) }

    var downloadProgress by remember { mutableStateOf<SdkDownloadProgress?>(null) }
    var kotlincProgress by remember { mutableStateOf<KotlincDownloadProgress?>(null) }
    val cancelFlag = remember { AtomicBoolean(false) }

    var pendingImportApi by remember { mutableIntStateOf(recommendedApi.coerceIn(30, 37)) }
    var isImporting by remember { mutableStateOf(false) }
    var isImportingKotlinc by remember { mutableStateOf(false) }

    var showKotlincUrlDialog by remember { mutableStateOf(false) }
    var showCreateKeystoreDialog by remember { mutableStateOf(false) }
    var importedKeystoreFile by remember { mutableStateOf<File?>(null) }

    val importPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isImporting = true
            scope.launch {
                val (_, msg) = SdkEngineManager.importAndroidJarFromUri(context, uri, pendingImportApi)
                isImporting = false
                refreshTick++
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val kotlincImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isImportingKotlinc = true
            scope.launch {
                val (_, msg) = SdkEngineManager.importKotlincPackFromUri(context, uri)
                isImportingKotlinc = false
                refreshTick++
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val keystoreImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val dest = File(SdkEngineManager.getKeystoresDir(context), "imported_${System.currentTimeMillis()}.keystore")
                val copied = try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BufferedOutputStream(FileOutputStream(dest)).use { bos ->
                            input.copyTo(bos)
                        }
                    }
                    dest.exists() && dest.length() > 0L
                } catch (_: Exception) {
                    false
                }
                withContext(Dispatchers.Main) {
                    if (copied) {
                        importedKeystoreFile = dest
                    } else {
                        Toast.makeText(context, "Could not read selected keystore file.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(refreshTick) {
        aapt2Status = SdkEngineManager.inspectAapt2Health(context)
        kotlincStatus = SdkEngineManager.getKotlincPackStatus(context)
        installedApis = SdkEngineManager.getInstalledApiLevels(context)
        savedKeystore = SdkEngineManager.loadKeystoreConfig(context)
        cacheStats = SdkEngineManager.getCacheStats(context)
    }

    val isBusy = downloadProgress != null || kotlincProgress != null || isImporting || isImportingKotlinc
    // Zero-Setup Auto-Pilot: শুধু AAPT2 থাকলেই সরাসরি বিল্ড করা যাবে; SDK ও Kotlinc না থাকলে অটো সেটআপ হবে!
    val canBuildAutoPilot = aapt2Status?.isExecutable == true

    Dialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF131419),
            border = BorderStroke(
                1.dp,
                if (canBuildAutoPilot) NeonLime.copy(alpha = 0.45f) else Color(0xFFFFB020).copy(alpha = 0.45f)
            ),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.86f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // ================= হেডার =================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(NeonLime.copy(alpha = 0.15f))
                            .border(0.8.dp, NeonLime.copy(alpha = 0.4f), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Build, contentDescription = null, tint = NeonLime, modifier = Modifier.size(19.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Native Engine Manager", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Zero-Setup Auto-Pilot • Kotlin/Compose + Java + D8/R8", color = TextSecondary, fontSize = 10.sp)
                    }
                    IconButton(
                        onClick = { refreshTick++ },
                        enabled = !isBusy,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = NeonLime, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { if (!isBusy) onDismiss() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ================= ১. AAPT2 ARM64 স্ট্যাটাস কার্ড =================
                    item {
                        val st = aapt2Status
                        val aaptOk = st?.isExecutable == true
                        val cardColor = if (aaptOk) NeonLime else Color(0xFFFF5252)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1A1B22))
                                .border(1.dp, cardColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (aaptOk) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                                    contentDescription = null,
                                    tint = cardColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (aaptOk) "ARM64 AAPT2: Active" else "ARM64 AAPT2: Action Required",
                                    color = if (aaptOk) NeonLime else Color(0xFFFF8A80),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(cardColor.copy(alpha = 0.16f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (aaptOk) "READY" else "MISSING",
                                        color = cardColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = st?.message ?: "Checking AAPT2 binary...",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    // ================= ২. Kotlin + Compose Compiler Pack Card =================
                    item {
                        val kSt = kotlincStatus
                        val kOk = kSt?.isInstalled == true
                        val cardBorder = if (kOk) NeonLime.copy(alpha = 0.45f) else Color(0xFFA855F7).copy(alpha = 0.4f)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (kOk) NeonLime.copy(alpha = 0.06f) else Color(0xFF1A1B22))
                                .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Code,
                                    contentDescription = null,
                                    tint = if (kOk) NeonLime else Color(0xFFA855F7),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Kotlin + Compose Compiler Pack",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background((if (kOk) NeonLime else Color(0xFFA855F7)).copy(alpha = 0.18f))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = if (kOk) "INSTALLED" else "AUTO-PILOT",
                                                color = if (kOk) NeonLime else Color(0xFFD8B4FE),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (kOk) {
                                            "Kotlin ${kSt!!.kotlinVersion} • ${if (kSt.composePluginAvailable) "Compose IR" else "Core"} • ${formatSize(kSt.totalSizeBytes)}"
                                        } else {
                                            "Auto-downloads on first Kotlin build, or tap Get / Import ZIP"
                                        },
                                        color = if (kOk) NeonLime.copy(alpha = 0.9f) else TextSecondary,
                                        fontSize = 10.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (kOk) {
                                    IconButton(
                                        onClick = {
                                            SdkEngineManager.deleteKotlincPack(context)
                                            refreshTick++
                                            Toast.makeText(context, "Removed Kotlin Compiler Pack", Toast.LENGTH_SHORT).show()
                                        },
                                        enabled = !isBusy,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color(0xFFFF5252), modifier = Modifier.size(17.dp))
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            cancelFlag.set(false)
                                            kotlincProgress = KotlincDownloadProgress(0L, 0L, "Starting...")
                                            scope.launch {
                                                val (_, msg) = SdkEngineManager.downloadKotlincPack(
                                                    context = context,
                                                    isCancelled = { cancelFlag.get() },
                                                    onProgress = { p -> kotlincProgress = p }
                                                )
                                                kotlincProgress = null
                                                refreshTick++
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        enabled = !isBusy,
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                                        shape = RoundedCornerShape(9.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Get", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (kotlincProgress != null) {
                                val kp = kotlincProgress!!
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = kp.phase,
                                        color = NeonLime,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "Cancel",
                                        color = Color(0xFFFF5252),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { cancelFlag.set(true) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                if (kp.totalBytes > 0L) {
                                    val frac = (kp.downloadedBytes.toFloat() / kp.totalBytes.toFloat()).coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = { frac },
                                        color = NeonLime,
                                        trackColor = Color(0xFF2B2D35),
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "${(frac * 100).toInt()}% (${formatSize(kp.downloadedBytes)} / ${formatSize(kp.totalBytes)})",
                                        color = Color.LightGray,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        color = NeonLime,
                                        trackColor = Color(0xFF2B2D35),
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { kotlincImportLauncher.launch(arrayOf("application/zip", "*/*")) },
                                    enabled = !isBusy,
                                    border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(9.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.weight(1f).height(34.dp)
                                ) {
                                    if (isImportingKotlinc) {
                                        CircularProgressIndicator(color = Color(0xFFD8B4FE), strokeWidth = 2.dp, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("Importing...", color = Color(0xFFD8B4FE), fontSize = 10.sp)
                                    } else {
                                        Icon(Icons.Outlined.Folder, contentDescription = null, tint = Color(0xFFD8B4FE), modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("Import Pack ZIP", color = Color(0xFFD8B4FE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { showKotlincUrlDialog = true },
                                    enabled = !isBusy,
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                                    shape = RoundedCornerShape(9.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Outlined.Link, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pack URL", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    // ================= ৩. Custom Release Signing Keystore Card =================
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1A1B22))
                                .border(
                                    1.dp,
                                    if (savedKeystore != null) NeonLime.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = if (savedKeystore != null) NeonLime else Color(0xFF58A6FF),
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Release Signing Keystore", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (savedKeystore != null) {
                                            "Active: ${savedKeystore!!.keystoreFile.name} (alias: ${savedKeystore!!.keyAlias})"
                                        } else {
                                            "Optional: Generate or import .p12/.jks for Release APK signing"
                                        },
                                        color = if (savedKeystore != null) NeonLime else TextSecondary,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showCreateKeystoreDialog = true },
                                    enabled = !isBusy,
                                    border = BorderStroke(1.dp, Color(0xFF58A6FF).copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(9.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.weight(1f).height(34.dp)
                                ) {
                                    Icon(Icons.Outlined.Key, contentDescription = null, tint = Color(0xFF58A6FF), modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Create", color = Color(0xFF58A6FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { keystoreImportLauncher.launch(arrayOf("*/*")) },
                                    enabled = !isBusy,
                                    border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.45f)),
                                    shape = RoundedCornerShape(9.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.weight(1f).height(34.dp)
                                ) {
                                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = NeonLime, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Import", color = NeonLime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                if (savedKeystore != null) {
                                    OutlinedButton(
                                        onClick = {
                                            SdkEngineManager.saveKeystoreConfig(context, null)
                                            refreshTick++
                                            Toast.makeText(context, "Cleared custom keystore (using Debug key)", Toast.LENGTH_SHORT).show()
                                        },
                                        enabled = !isBusy,
                                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.45f)),
                                        shape = RoundedCornerShape(9.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Clear", color = Color(0xFFFF8A80), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // ================= ৪. কাস্টম android.jar ইমপোর্ট কার্ড =================
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1A1B22))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ANDROID SDK PLATFORMS (android.jar)",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (installedApis.isNotEmpty()) "${installedApis.size} installed" else "Auto-Pilot",
                                    color = if (installedApis.isNotEmpty()) NeonLime else Color(0xFF58A6FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Missing SDK platforms download automatically on first build.",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    pendingImportApi = recommendedApi.coerceIn(30, 37)
                                    importPickerLauncher.launch(arrayOf("application/java-archive", "application/zip", "*/*"))
                                },
                                enabled = !isBusy,
                                border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.45f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(38.dp)
                            ) {
                                if (isImporting) {
                                    CircularProgressIndicator(color = NeonLime, strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Importing...", color = NeonLime, fontSize = 11.sp)
                                } else {
                                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = NeonLime, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Import android.jar or platform.zip (API $recommendedApi)", color = NeonLime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // ================= ৫. SDK ডাউনলোড প্রোগ্রেস কার্ড =================
                    if (downloadProgress != null) {
                        val dp = downloadProgress!!
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF1F231A))
                                    .border(1.dp, NeonLime.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Downloading API ${dp.apiLevel}...",
                                        color = NeonLime,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "Cancel",
                                        color = Color(0xFFFF5252),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { cancelFlag.set(true) }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                if (dp.totalBytes > 0L) {
                                    val frac = (dp.downloadedBytes.toFloat() / dp.totalBytes.toFloat()).coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = { frac },
                                        color = NeonLime,
                                        trackColor = Color(0xFF2B2D35),
                                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Text(
                                        text = "${dp.phase} • ${(frac * 100).toInt()}% (${formatSize(dp.downloadedBytes)} / ${formatSize(dp.totalBytes)})",
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        color = NeonLime,
                                        trackColor = Color(0xFF2B2D35),
                                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Text(
                                        text = "${dp.phase} (${formatSize(dp.downloadedBytes)})",
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    // ================= ৬. প্ল্যাটফর্ম SDK লিস্ট =================
                    items(SdkEngineManager.SUPPORTED_PLATFORMS, key = { it.apiLevel }) { spec ->
                        val isInstalled = spec.apiLevel in installedApis
                        val isRecommended = spec.apiLevel == recommendedApi
                        val isDownloadingThis = downloadProgress?.apiLevel == spec.apiLevel

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isInstalled) NeonLime.copy(alpha = 0.08f) else Color(0xFF1A1B22))
                                .border(
                                    1.dp,
                                    if (isInstalled) NeonLime.copy(alpha = 0.45f)
                                    else if (isRecommended) Color(0xFF58A6FF).copy(alpha = 0.4f)
                                    else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = spec.title,
                                        color = if (isInstalled) NeonLime else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isRecommended) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF58A6FF).copy(alpha = 0.2f))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text("PROJECT", color = Color(0xFF58A6FF), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text(
                                    text = if (isInstalled) {
                                        val size = SdkEngineManager.getAndroidJarFile(context, spec.apiLevel).length()
                                        "${spec.subtitle} • Installed (${formatSize(size)})"
                                    } else spec.subtitle,
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isInstalled) {
                                IconButton(
                                    onClick = {
                                        SdkEngineManager.deletePlatform(context, spec.apiLevel)
                                        refreshTick++
                                        Toast.makeText(context, "Removed API ${spec.apiLevel}", Toast.LENGTH_SHORT).show()
                                    },
                                    enabled = !isBusy,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(17.dp))
                                }
                            } else {
                                Button(
                                    onClick = {
                                        cancelFlag.set(false)
                                        downloadProgress = SdkDownloadProgress(spec.apiLevel, 0L, 0L, "Starting...")
                                        scope.launch {
                                            val (_, msg) = SdkEngineManager.downloadPlatformSdk(
                                                context = context,
                                                spec = spec,
                                                isCancelled = { cancelFlag.get() },
                                                onProgress = { p -> downloadProgress = p }
                                            )
                                            downloadProgress = null
                                            refreshTick++
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    enabled = !isBusy,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDownloadingThis) Color(0xFF282830) else NeonLime,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(9.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Get", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // ================= ৭. ক্যাশ ম্যানেজার কার্ড =================
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1A1B22))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Maven & Dex Caches", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Total: ${formatSize(cacheStats.totalBytes)} (Maven ${formatSize(cacheStats.mavenRepoBytes)}, ExtDex ${formatSize(cacheStats.extDexBytes)})",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        SdkEngineManager.clearAllCaches(context)
                                        refreshTick++
                                        Toast.makeText(context, "Engine caches cleared!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = cacheStats.totalBytes > 0L && !isBusy,
                                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(9.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = null, tint = Color(0xFFFF8A80), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear", color = Color(0xFFFF8A80), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ================= ফুটার বাটন =================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF23242C), contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("Close", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    if (onReadyToBuild != null) {
                        Button(
                            onClick = {
                                onDismiss()
                                onReadyToBuild()
                            },
                            enabled = canBuildAutoPilot && !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.3f).height(44.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Build Now", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // ================= ৮. Kotlin Pack Custom URL ডায়ালগ =================
    if (showKotlincUrlDialog) {
        var packUrl by remember { mutableStateOf(SdkEngineManager.getKotlincPackUrl(context)) }
        Dialog(onDismissRequest = { showKotlincUrlDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF16171D),
                border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Kotlin + Compose Pack URL", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Direct download link to kotlinc-compose-pack.zip (automatically used on first Kotlin build).",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = packUrl,
                        onValueChange = { packUrl = it },
                        label = { Text("Download URL", fontSize = 11.sp) },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = NeonLime
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { packUrl = SdkEngineManager.DEFAULT_KOTLINC_PACK_URL },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset Default", color = Color(0xFF58A6FF), fontSize = 11.sp)
                        }
                        Button(
                            onClick = { showKotlincUrlDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26262E), contentColor = Color.White)
                        ) { Text("Cancel", fontSize = 11.sp) }
                        Button(
                            onClick = {
                                SdkEngineManager.saveKotlincPackUrl(context, packUrl)
                                showKotlincUrlDialog = false
                                refreshTick++
                                Toast.makeText(context, "Compiler Pack URL saved!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black)
                        ) { Text("Save", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                    }
                }
            }
        }
    }

    // ================= ৯. Create Keystore ডায়ালগ =================
    if (showCreateKeystoreDialog) {
        var fileName by remember { mutableStateOf("release_key") }
        var alias by remember { mutableStateOf("key0") }
        var storePass by remember { mutableStateOf("") }
        var keyPass by remember { mutableStateOf("") }
        var commonName by remember { mutableStateOf("CodeStudio Developer") }

        Dialog(onDismissRequest = { showCreateKeystoreDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF16171D),
                border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Generate Release Keystore (.p12)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("File Name", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonLime),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it },
                        label = { Text("Key Alias", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonLime),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = storePass,
                        onValueChange = { storePass = it },
                        label = { Text("Store Password (min 6 chars)", fontSize = 11.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonLime),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = keyPass,
                        onValueChange = { keyPass = it },
                        label = { Text("Key Password (optional = same)", fontSize = 11.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonLime),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = commonName,
                        onValueChange = { commonName = it },
                        label = { Text("Organization / Developer Name", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonLime),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { showCreateKeystoreDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26262E), contentColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) { Text("Cancel") }
                        Button(
                            onClick = {
                                if (storePass.length < 6 || alias.isBlank()) {
                                    Toast.makeText(context, "Enter alias and password (6+ chars)", Toast.LENGTH_SHORT).show()
                                } else {
                                    val finalKp = keyPass.ifEmpty { storePass }
                                    val (ok, cfg) = SdkEngineManager.generateReleaseKeystore(context, fileName, storePass, alias.trim(), finalKp, commonName)
                                    if (ok && cfg != null) {
                                        showCreateKeystoreDialog = false
                                        refreshTick++
                                        Toast.makeText(context, "Keystore saved: ${cfg.keystoreFile.name}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed generating keystore", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                            modifier = Modifier.weight(1.3f)
                        ) { Text("Generate", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    // ================= ১০. Imported Keystore Credentials ডায়ালগ =================
    if (importedKeystoreFile != null) {
        val ksFile = importedKeystoreFile!!
        var alias by remember { mutableStateOf("key0") }
        var storePass by remember { mutableStateOf("") }
        var keyPass by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { importedKeystoreFile = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF16171D),
                border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Configure Imported Keystore", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it },
                        label = { Text("Key Alias", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonLime),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = storePass,
                        onValueChange = { storePass = it },
                        label = { Text("Store Password", fontSize = 11.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonLime),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = keyPass,
                        onValueChange = { keyPass = it },
                        label = { Text("Key Password (optional = same)", fontSize = 11.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonLime),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { importedKeystoreFile = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26262E), contentColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) { Text("Cancel") }
                        Button(
                            onClick = {
                                if (alias.isBlank() || storePass.isEmpty()) {
                                    Toast.makeText(context, "Alias & password required", Toast.LENGTH_SHORT).show()
                                } else {
                                    SdkEngineManager.saveKeystoreConfig(
                                        context,
                                        CustomKeystoreConfig(ksFile, storePass, alias.trim(), keyPass.ifEmpty { storePass })
                                    )
                                    importedKeystoreFile = null
                                    refreshTick++
                                    Toast.makeText(context, "Custom keystore saved!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                            modifier = Modifier.weight(1.3f)
                        ) { Text("Save", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}