package com.shd.codestudio.nativeengine

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
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
    var lowMemoryMode by remember { mutableStateOf(SdkEngineManager.isLowMemoryMode(context)) }
    var hasBundledOffline by remember { mutableStateOf(SdkEngineManager.hasBundledEngineAssets(context)) }
    var refreshTick by remember { mutableIntStateOf(0) }

    var showAdvancedSdk by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<SdkDownloadProgress?>(null) }
    val cancelFlag = remember { AtomicBoolean(false) }

    var pendingImportApi by remember { mutableIntStateOf(recommendedApi.coerceIn(30, 37)) }
    var isImporting by remember { mutableStateOf(false) }
    var isImportingKotlinc by remember { mutableStateOf(false) }

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
        SdkEngineManager.ensureBundledOfflineEngineReady(context)
        aapt2Status = SdkEngineManager.inspectAapt2Health(context)
        kotlincStatus = SdkEngineManager.getKotlincPackStatus(context)
        installedApis = SdkEngineManager.getInstalledApiLevels(context)
        savedKeystore = SdkEngineManager.loadKeystoreConfig(context)
        cacheStats = SdkEngineManager.getCacheStats(context)
        lowMemoryMode = SdkEngineManager.isLowMemoryMode(context)
        hasBundledOffline = SdkEngineManager.hasBundledEngineAssets(context)
    }

    val isBusy = downloadProgress != null || isImporting || isImportingKotlinc
    val aaptReady = aapt2Status?.isExecutable == true
    val engineReady = aaptReady && ((kotlincStatus?.isInstalled == true) || hasBundledOffline)

    Dialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF131419),
            border = BorderStroke(
                1.dp,
                if (aaptReady) NeonLime.copy(alpha = 0.45f) else Color(0xFFFFB020).copy(alpha = 0.45f)
            ),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.82f)
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
                        Text("Build Runtime & Signing", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("All-in-One Offline Engine • No Setup Required", color = TextSecondary, fontSize = 10.sp)
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
                    // ================= ১. All-in-One Build Runtime Status Card =================
                    item {
                        val kSt = kotlincStatus
                        val cardColor = if (engineReady) NeonLime else if (aaptReady) Color(0xFF58A6FF) else Color(0xFFFF5252)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1A1B22))
                                .border(1.dp, cardColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (aaptReady) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                                    contentDescription = null,
                                    tint = cardColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (aaptReady) "Offline Build Runtime" else "AAPT2 Missing",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(cardColor.copy(alpha = 0.16f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = if (engineReady) "READY" else if (aaptReady) "AUTO-PILOT" else "ERROR",
                                        color = cardColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = if (aaptReady) {
                                    "Built-in ARM64 toolchain runs directly on device without Gradle daemon."
                                } else {
                                    aapt2Status?.message ?: "Checking AAPT2 binary..."
                                },
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // ক্যাপাবিলিটি চিপস (১ লাইনে ক্লিন হরাইজন্টাল স্ক্রল)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CapabilityChip("AAPT2", aaptReady)
                                CapabilityChip("Kotlin ${kSt?.kotlinVersion ?: "2.4.21"}", kSt?.isInstalled == true || hasBundledOffline)
                                CapabilityChip("Compose IR", kSt?.composePluginAvailable == true || hasBundledOffline)
                                CapabilityChip("Serialization", kSt?.serializationAvailable == true || hasBundledOffline)
                                CapabilityChip("Parcelize", kSt?.parcelizeAvailable == true || hasBundledOffline)
                                CapabilityChip("SDK ${installedApis.firstOrNull() ?: recommendedApi}", installedApis.isNotEmpty() || hasBundledOffline)
                                CapabilityChip("D8 / R8", true)
                            }
                        }
                    }

                    // ================= ২. Performance & Low Memory Mode (CodeAssist Style) =================
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1A1B22))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                .clickable {
                                    val next = !lowMemoryMode
                                    lowMemoryMode = next
                                    SdkEngineManager.setLowMemoryMode(context, next)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Memory,
                                contentDescription = null,
                                tint = if (lowMemoryMode) Color(0xFFFFB020) else NeonLime,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Low Memory Mode", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (lowMemoryMode) {
                                        "1-Core Sequential Dexing (Saves RAM)"
                                    } else {
                                        "4-Core Parallel Pre-Dexing (Fastest)"
                                    },
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            Switch(
                                checked = lowMemoryMode,
                                onCheckedChange = {
                                    lowMemoryMode = it
                                    SdkEngineManager.setLowMemoryMode(context, it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = Color(0xFFFFB020),
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFF2D2F3A)
                                )
                            )
                        }
                    }

                    // ================= ৩. Release Signing Keystore Card =================
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
                                            "Optional: Generate or import .p12/.jks for Release builds"
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

                    // ================= ৪. ক্যাশ ম্যানেজার কার্ড =================
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
                                Text("Prebuilt Dex & Maven Caches", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Total: ${formatSize(cacheStats.totalBytes)} (Maven ${formatSize(cacheStats.mavenRepoBytes)}, Pre-Dex ${formatSize(cacheStats.extDexBytes)})",
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

                    // ================= ৫. কোলাপ্সড Advanced Platforms সেকশন =================
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF17181E))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showAdvancedSdk = !showAdvancedSdk }
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Tune, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Advanced: Additional SDKs & Custom Pack", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "Installed APIs: ${if (installedApis.isEmpty()) "Built-in API 36" else installedApis.joinToString(", ")}",
                                        color = TextSecondary,
                                        fontSize = 9.sp
                                    )
                                }
                                Icon(
                                    imageVector = if (showAdvancedSdk) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            AnimatedVisibility(visible = showAdvancedSdk) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                                    if (downloadProgress != null) {
                                        val dp = downloadProgress!!
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF1F231A))
                                                .border(1.dp, NeonLime.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                                .padding(10.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Downloading API ${dp.apiLevel}...", color = NeonLime, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                                Text(
                                                    "Cancel",
                                                    color = Color(0xFFFF5252),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.clickable { cancelFlag.set(true) }
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(5.dp))
                                            if (dp.totalBytes > 0L) {
                                                val frac = (dp.downloadedBytes.toFloat() / dp.totalBytes.toFloat()).coerceIn(0f, 1f)
                                                LinearProgressIndicator(progress = { frac }, color = NeonLime, trackColor = Color(0xFF2B2D35), modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape))
                                            } else {
                                                LinearProgressIndicator(color = NeonLime, trackColor = Color(0xFF2B2D35), modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape))
                                            }
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                pendingImportApi = recommendedApi.coerceIn(30, 37)
                                                importPickerLauncher.launch(arrayOf("application/java-archive", "application/zip", "*/*"))
                                            },
                                            enabled = !isBusy,
                                            border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.4f)),
                                            shape = RoundedCornerShape(9.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.weight(1f).height(34.dp)
                                        ) {
                                            Text("Import android.jar", color = NeonLime, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        }
                                        OutlinedButton(
                                            onClick = { kotlincImportLauncher.launch(arrayOf("application/zip", "*/*")) },
                                            enabled = !isBusy,
                                            border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.45f)),
                                            shape = RoundedCornerShape(9.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.weight(1f).height(34.dp)
                                        ) {
                                            Text("Import Compiler ZIP", color = Color(0xFFD8B4FE), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        }
                                    }

                                    SdkEngineManager.SUPPORTED_PLATFORMS.forEach { spec ->
                                        val isInstalled = spec.apiLevel in installedApis
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isInstalled) NeonLime.copy(alpha = 0.08f) else Color(0xFF1F2028))
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(spec.title, color = if (isInstalled) NeonLime else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text(
                                                    text = if (isInstalled) {
                                                        "Installed (${formatSize(SdkEngineManager.getAndroidJarFile(context, spec.apiLevel).length())})"
                                                    } else spec.subtitle,
                                                    color = TextSecondary,
                                                    fontSize = 9.sp
                                                )
                                            }
                                            if (isInstalled) {
                                                IconButton(
                                                    onClick = {
                                                        SdkEngineManager.deletePlatform(context, spec.apiLevel)
                                                        refreshTick++
                                                    },
                                                    enabled = !isBusy,
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(15.dp))
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
                                                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                                                    shape = RoundedCornerShape(7.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Get", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
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
                            enabled = aaptReady && !isBusy,
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

    // ================= Create Keystore ডায়ালগ =================
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

    // ================= Imported Keystore Credentials ডায়ালগ =================
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

@Composable
private fun CapabilityChip(
    label: String,
    active: Boolean
) {
    val bg = if (active) NeonLime.copy(alpha = 0.14f) else Color(0xFF252630)
    val border = if (active) NeonLime.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f)
    val txtColor = if (active) NeonLime else TextSecondary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(0.7.dp, border, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (active) {
            Icon(Icons.Default.Check, contentDescription = null, tint = NeonLime, modifier = Modifier.size(10.dp))
            Spacer(modifier = Modifier.width(3.dp))
        }
        Text(
            text = label,
            color = txtColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false
        )
    }
}