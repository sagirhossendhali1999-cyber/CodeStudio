package com.shd.codestudio.nativeengine

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class SdkPlatformSpec(
    val apiLevel: Int,
    val title: String,
    val subtitle: String,
    val downloadUrls: List<String>
)

data class Aapt2HealthStatus(
    val isAvailable: Boolean,
    val isExecutable: Boolean,
    val binaryPath: String,
    val versionText: String,
    val message: String
)

data class SdkDownloadProgress(
    val apiLevel: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val phase: String
)

data class KotlincPackStatus(
    val isInstalled: Boolean,
    val kotlinVersion: String,
    val composePluginAvailable: Boolean,
    val stdlibAvailable: Boolean,
    val totalSizeBytes: Long,
    val packSourceUrl: String
)

data class KotlincDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val phase: String
)

data class EngineCacheStats(
    val mavenRepoBytes: Long,
    val unpackedAarBytes: Long,
    val extDexBytes: Long,
    val compiledResBytes: Long
) {
    val totalBytes: Long
        get() = mavenRepoBytes + unpackedAarBytes + extDexBytes + compiledResBytes
}

private class ByteTrackingInputStream(input: InputStream) : FilterInputStream(input) {
    @Volatile
    var totalRead: Long = 0L
        private set

    override fun read(): Int {
        val b = super.read()
        if (b != -1) totalRead++
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = super.read(b, off, len)
        if (count > 0) totalRead += count
        return count
    }
}

object SdkEngineManager {

    private const val PREFS_ENGINE = "CodeStudioEnginePrefs"
    private const val KEY_KOTLINC_PACK_URL = "kotlinc_pack_url"
    private const val ASSET_KOTLINC_PACK = "kotlinc-compose-pack.zip"

    const val DEFAULT_KOTLINC_PACK_URL =
        "https://github.com/sagirhossendhali1999-cyber/CodeStudio/releases/download/engine-kotlinc-latest/kotlinc-compose-pack.zip"

    val SUPPORTED_PLATFORMS = listOf(
        SdkPlatformSpec(
            apiLevel = 36,
            title = "Android 16 (API 36)",
            subtitle = "Baklava • Recommended",
            downloadUrls = listOf(
                "https://dl.google.com/android/repository/platform-36_r01.zip"
            )
        ),
        SdkPlatformSpec(
            apiLevel = 35,
            title = "Android 15 (API 35)",
            subtitle = "VanillaIceCream",
            downloadUrls = listOf(
                "https://dl.google.com/android/repository/platform-35_r02.zip",
                "https://dl.google.com/android/repository/platform-35_r01.zip"
            )
        ),
        SdkPlatformSpec(
            apiLevel = 34,
            title = "Android 14 (API 34)",
            subtitle = "UpsideDownCake",
            downloadUrls = listOf(
                "https://dl.google.com/android/repository/platform-34_r03.zip",
                "https://dl.google.com/android/repository/platform-34_r02.zip",
                "https://dl.google.com/android/repository/platform-34_r01.zip"
            )
        ),
        SdkPlatformSpec(
            apiLevel = 33,
            title = "Android 13 (API 33)",
            subtitle = "Tiramisu",
            downloadUrls = listOf(
                "https://dl.google.com/android/repository/platform-33_r03.zip",
                "https://dl.google.com/android/repository/platform-33_r02.zip",
                "https://dl.google.com/android/repository/platform-33_r01.zip"
            )
        ),
        SdkPlatformSpec(
            apiLevel = 30,
            title = "Android 11 (API 30)",
            subtitle = "R • Minimum IDE Target",
            downloadUrls = listOf(
                "https://dl.google.com/android/repository/platform-30_r03.zip",
                "https://dl.google.com/android/repository/platform-30_r02.zip",
                "https://dl.google.com/android/repository/platform-30_r01.zip"
            )
        ),
        SdkPlatformSpec(
            apiLevel = 37,
            title = "Android 17 (API 37)",
            subtitle = "Latest Preview",
            downloadUrls = listOf(
                "https://dl.google.com/android/repository/platform-37.1_r01.zip",
                "https://dl.google.com/android/repository/platform-37.0_r01.zip"
            )
        )
    )

    // ================= ১. Android Platform SDK ম্যানেজমেন্ট =================
    fun getPlatformsDir(context: Context): File =
        File(context.filesDir, "CodeStudioEngine/android-sdk/platforms").apply { if (!exists()) mkdirs() }

    fun getAndroidJarFile(context: Context, apiLevel: Int): File =
        File(getPlatformsDir(context), "android-$apiLevel/android.jar")

    fun isPlatformInstalled(context: Context, apiLevel: Int): Boolean {
        val jar = getAndroidJarFile(context, apiLevel)
        return jar.exists() && jar.isFile && jar.length() > 100_000L
    }

    fun getInstalledApiLevels(context: Context): List<Int> {
        val dir = getPlatformsDir(context)
        return dir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("android-") }
            ?.mapNotNull { folder ->
                val api = folder.name.removePrefix("android-").toIntOrNull()
                if (api != null && isPlatformInstalled(context, api)) api else null
            }
            ?.sortedDescending()
            ?: emptyList()
    }

    // স্বয়ংক্রিয় জিরো-সেটআপ: অ্যান্ড্রয়েড SDK নিশ্চিতকরণ
    suspend fun autoEnsureAndroidJar(
        context: Context,
        targetApi: Int,
        isCancelled: () -> Boolean = { false },
        onLog: (String) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        val existing = Aapt2ResourcePipeline.resolveAndroidJar(context, targetApi)
        if (existing != null && existing.exists()) {
            return@withContext existing
        }

        val spec = SUPPORTED_PLATFORMS.firstOrNull { it.apiLevel == targetApi }
            ?: SUPPORTED_PLATFORMS.firstOrNull { it.apiLevel == 36 }
            ?: SUPPORTED_PLATFORMS.first()

        onLog("[Auto-Pilot] Setting up Android SDK Platform (${spec.title}) for first-time build...")

        var lastLoggedPct = -10
        val (ok, errMsg) = downloadPlatformSdk(
            context = context,
            spec = spec,
            isCancelled = isCancelled,
            onProgress = { p ->
                if (p.totalBytes > 0L) {
                    val pct = ((p.downloadedBytes.toFloat() / p.totalBytes.toFloat()) * 100).toInt()
                    if (pct >= lastLoggedPct + 15 || pct == 100) {
                        lastLoggedPct = pct
                        onLog("[Auto-Pilot] Android SDK: ${p.phase} ($pct%)")
                    }
                }
            }
        )

        if (ok) {
            val jar = getAndroidJarFile(context, spec.apiLevel)
            if (jar.exists() && jar.isFile) {
                onLog("[Auto-Pilot] Android SDK ready!")
                return@withContext jar
            }
        } else {
            onLog("[Auto-Pilot] WARN: SDK download issue: $errMsg")
        }

        Aapt2ResourcePipeline.resolveAndroidJar(context, targetApi)
    }

    fun deletePlatform(context: Context, apiLevel: Int): Boolean {
        val dir = File(getPlatformsDir(context), "android-$apiLevel")
        return if (dir.exists()) deleteRecursivelySafe(dir) else true
    }

    // ================= ২. Zero-Setup Kotlin + Compose Compiler Pack ম্যানেজমেন্ট =================
    fun getKotlincDir(context: Context): File =
        File(context.filesDir, "CodeStudioEngine/kotlinc").apply { if (!exists()) mkdirs() }

    fun getKotlincDexDir(context: Context): File =
        File(getKotlincDir(context), "dex").apply { if (!exists()) mkdirs() }

    fun getComposePluginJar(context: Context): File =
        File(getKotlincDir(context), "plugins/compose-compiler.jar")

    fun getBundledKotlinStdlibJar(context: Context): File =
        File(getKotlincDir(context), "stdlib/kotlin-stdlib.jar")

    fun getKotlincPackUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_ENGINE, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_KOTLINC_PACK_URL, DEFAULT_KOTLINC_PACK_URL)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_KOTLINC_PACK_URL

        if (saved.contains("sagirhossenali1999-cyber")) {
            prefs.edit().putString(KEY_KOTLINC_PACK_URL, DEFAULT_KOTLINC_PACK_URL).apply()
            return DEFAULT_KOTLINC_PACK_URL
        }
        return saved
    }

    fun saveKotlincPackUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_ENGINE, Context.MODE_PRIVATE)
        val clean = url.trim()
        if (clean.isEmpty()) {
            prefs.edit().remove(KEY_KOTLINC_PACK_URL).apply()
        } else {
            prefs.edit().putString(KEY_KOTLINC_PACK_URL, clean).apply()
        }
    }

    fun isKotlincInstalled(context: Context): Boolean {
        val dexDir = getKotlincDexDir(context)
        val dexFiles = dexDir.listFiles()?.filter {
            it.isFile &&
                !it.name.equals("kotlinc_resources.zip", ignoreCase = true) &&
                (it.name.endsWith(".dex", true) || it.name.endsWith(".zip", true) || it.name.endsWith(".jar", true)) &&
                it.length() > 100_000L
        }
        return !dexFiles.isNullOrEmpty()
    }

    fun getKotlincPackStatus(context: Context): KotlincPackStatus {
        val root = getKotlincDir(context)
        val installed = isKotlincInstalled(context)
        val composeOk = getComposePluginJar(context).let { it.exists() && it.length() > 0L }
        val stdlibOk = getBundledKotlinStdlibJar(context).let { it.exists() && it.length() > 0L }
        val manifestFile = File(root, "manifest.json")
        val kotlinVer = if (manifestFile.exists()) {
            runCatching { JSONObject(manifestFile.readText(Charsets.UTF_8)).optString("kotlinVersion", "2.4.21") }
                .getOrDefault("2.4.21")
        } else "2.4.21"

        val size = if (installed) folderSize(root) else 0L
        return KotlincPackStatus(
            isInstalled = installed,
            kotlinVersion = kotlinVer,
            composePluginAvailable = composeOk,
            stdlibAvailable = stdlibOk,
            totalSizeBytes = size,
            packSourceUrl = getKotlincPackUrl(context)
        )
    }

    fun deleteKotlincPack(context: Context): Boolean {
        KotlinNativeCompiler.invalidateCachedLoader()
        val dir = getKotlincDir(context)
        return if (dir.exists()) deleteRecursivelySafe(dir) else true
    }

    // স্বয়ংক্রিয় জিরো-সেটআপ: Kotlin + Compose Compiler Pack নিশ্চিতকরণ
    suspend fun autoEnsureKotlincPack(
        context: Context,
        isCancelled: () -> Boolean = { false },
        onLog: (String) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (isKotlincInstalled(context)) {
            return@withContext true to "Kotlin + Compose Compiler Pack ready."
        }

        // ১. যদি অ্যাপের assets/-এ বান্ডেল করা থাকে, সম্পূর্ণ অফলাইনে এক্সট্র্যাক্ট করা
        val hasBundledAsset = runCatching {
            context.assets.list("")?.contains(ASSET_KOTLINC_PACK) == true
        }.getOrDefault(false)

        if (hasBundledAsset) {
            onLog("[Auto-Pilot] Extracting built-in Kotlin + Compose Compiler Pack...")
            val tmpZip = File(context.cacheDir, "kotlinc_asset_tmp.zip")
            try {
                context.assets.open(ASSET_KOTLINC_PACK).use { input ->
                    BufferedOutputStream(FileOutputStream(tmpZip), 65_536).use { bos ->
                        input.copyTo(bos)
                    }
                }
                val res = installKotlincPackFromZipFile(context, tmpZip)
                tmpZip.delete()
                if (res.first) {
                    onLog("[Auto-Pilot] Built-in Kotlin Compiler Pack ready!")
                    return@withContext res
                }
            } catch (_: Exception) {
                tmpZip.delete()
            }
        }

        // ২. অটোমেটিক অন-ডিমান্ড ডাউনলোড
        onLog("[Auto-Pilot] Preparing Kotlin + Compose Compiler Pack (one-time setup)...")
        var lastLoggedPct = -15
        val (ok, msg) = downloadKotlincPack(
            context = context,
            isCancelled = isCancelled,
            onProgress = { p ->
                if (p.totalBytes > 0L) {
                    val pct = ((p.downloadedBytes.toFloat() / p.totalBytes.toFloat()) * 100).toInt()
                    if (pct >= lastLoggedPct + 15 || pct == 100) {
                        lastLoggedPct = pct
                        onLog("[Auto-Pilot] Kotlin Pack: ${p.phase} ($pct%)")
                    }
                }
            }
        )
        if (ok) {
            onLog("[Auto-Pilot] Kotlin + Compose Compiler Pack installed!")
        } else {
            onLog("[Auto-Pilot] ERROR: $msg")
        }
        ok to msg
    }

    suspend fun downloadKotlincPack(
        context: Context,
        isCancelled: () -> Boolean,
        onProgress: (KotlincDownloadProgress) -> Unit
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val packUrl = getKotlincPackUrl(context)
        val tmpZip = File(context.cacheDir, "kotlinc_pack_download.zip.tmp")
        var conn: HttpURLConnection? = null

        try {
            onProgress(KotlincDownloadProgress(0L, 0L, "Connecting to Compiler Repo..."))
            conn = (URL(packUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "CodeStudio-Android/2.0")
                connectTimeout = 20_000
                readTimeout = 60_000
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext false to "HTTP $code downloading Kotlin pack from $packUrl. Run 'Build Kotlin + Compose Engine Pack' GitHub workflow or import ZIP in Engine Manager."
            }

            val totalLen = conn.contentLengthLong.coerceAtLeast(0L)
            val tracker = ByteTrackingInputStream(conn.inputStream)
            val buf = ByteArray(65_536)
            var lastEmitMs = 0L

            BufferedInputStream(tracker, 65_536).use { bis ->
                BufferedOutputStream(FileOutputStream(tmpZip), 65_536).use { bos ->
                    var r: Int
                    while (bis.read(buf).also { r = it } > 0) {
                        if (isCancelled()) {
                            tmpZip.delete()
                            return@withContext false to "Download cancelled."
                        }
                        bos.write(buf, 0, r)
                        val now = System.currentTimeMillis()
                        if (now - lastEmitMs >= 120L) {
                            lastEmitMs = now
                            onProgress(KotlincDownloadProgress(tracker.totalRead, totalLen, "Downloading Kotlin + Compose Pack..."))
                        }
                    }
                }
            }
            conn.disconnect()

            if (isCancelled()) {
                tmpZip.delete()
                return@withContext false to "Download cancelled."
            }

            onProgress(KotlincDownloadProgress(totalLen, totalLen, "Extracting compiler pack..."))
            val installRes = installKotlincPackFromZipFile(context, tmpZip)
            tmpZip.delete()
            installRes
        } catch (e: Exception) {
            tmpZip.delete()
            false to "Download error: ${e.message ?: "Network error"}"
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    suspend fun importKotlincPackFromUri(
        context: Context,
        uri: Uri
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val tmpZip = File(context.cacheDir, "kotlinc_pack_import.zip.tmp")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedOutputStream(FileOutputStream(tmpZip), 65_536).use { bos ->
                    input.copyTo(bos)
                }
            } ?: return@withContext false to "Cannot open selected file."

            if (!tmpZip.exists() || tmpZip.length() < 100_000L) {
                tmpZip.delete()
                return@withContext false to "Selected file is too small or invalid."
            }

            val res = installKotlincPackFromZipFile(context, tmpZip)
            tmpZip.delete()
            res
        } catch (e: Exception) {
            tmpZip.delete()
            false to "Import failed: ${e.message ?: "Invalid ZIP"}"
        }
    }

    private fun installKotlincPackFromZipFile(context: Context, zipFile: File): Pair<Boolean, String> {
        val stageDir = File(context.filesDir, "CodeStudioEngine/kotlinc_stage")
        if (stageDir.exists()) deleteRecursivelySafe(stageDir)
        stageDir.mkdirs()

        return try {
            val canonicalRoot = stageDir.canonicalPath + File.separator
            val buf = ByteArray(65_536)

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile), 65_536)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    var relName = entry.name.replace("\\", "/").trimStart('/')
                    if (relName.startsWith("./")) relName = relName.removePrefix("./")
                    if (relName.isNotEmpty()) {
                        val target = File(stageDir, relName)
                        if (target.canonicalPath == stageDir.canonicalPath || target.canonicalPath.startsWith(canonicalRoot)) {
                            if (entry.isDirectory) {
                                target.mkdirs()
                            } else {
                                target.parentFile?.mkdirs()
                                BufferedOutputStream(FileOutputStream(target), 65_536).use { bos ->
                                    var r: Int
                                    while (zis.read(buf).also { r = it } > 0) {
                                        bos.write(buf, 0, r)
                                    }
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val dexCandidate = File(stageDir, "dex")
            val effectiveStage = if (!dexCandidate.exists()) {
                val sub = stageDir.listFiles()?.firstOrNull { it.isDirectory && File(it, "dex").isDirectory }
                sub ?: stageDir
            } else {
                stageDir
            }

            val stagedDexDir = File(effectiveStage, "dex")
            val hasDexPayload = stagedDexDir.exists() && stagedDexDir.listFiles()?.any {
                it.isFile &&
                    !it.name.equals("kotlinc_resources.zip", ignoreCase = true) &&
                    (it.name.endsWith(".zip", true) || it.name.endsWith(".dex", true) || it.name.endsWith(".jar", true)) &&
                    it.length() > 100_000L
            } == true

            if (!hasDexPayload) {
                deleteRecursivelySafe(stageDir)
                return false to "Invalid Kotlin pack: missing dex/kotlinc_core.zip inside archive."
            }

            // Android 14+ (API 34+) security: ডেক্স ফাইল ও প্লাগইন read-only সেট করা
            effectiveStage.walkTopDown().forEach { f ->
                if (f.isFile && (f.parentFile?.name in setOf("dex", "plugins", "stdlib"))) {
                    runCatching { f.setReadOnly() }
                }
            }

            val targetDir = File(context.filesDir, "CodeStudioEngine/kotlinc")
            KotlinNativeCompiler.invalidateCachedLoader()
            if (targetDir.exists()) deleteRecursivelySafe(targetDir)

            if (effectiveStage == stageDir) {
                if (!stageDir.renameTo(targetDir)) {
                    stageDir.copyRecursively(targetDir, overwrite = true)
                    deleteRecursivelySafe(stageDir)
                }
            } else {
                effectiveStage.copyRecursively(targetDir, overwrite = true)
                deleteRecursivelySafe(stageDir)
            }

            targetDir.walkTopDown().forEach { f ->
                if (f.isFile && (f.parentFile?.name in setOf("dex", "plugins", "stdlib"))) {
                    runCatching { f.setReadOnly() }
                }
            }

            true to "Kotlin + Compose Compiler Pack ready!"
        } catch (e: Exception) {
            deleteRecursivelySafe(stageDir)
            false to "Extraction failed: ${e.message ?: "Error"}"
        }
    }

    private fun deleteRecursivelySafe(file: File): Boolean {
        if (!file.exists()) return true
        file.walkBottomDown().forEach { f ->
            runCatching { f.setWritable(true, false) }
            runCatching { f.delete() }
        }
        return !file.exists()
    }

    // ================= ৩. Custom Release Keystore Generator & Storage =================
    fun getKeystoresDir(context: Context): File =
        File(context.filesDir, "CodeStudioEngine/keystores").apply { if (!exists()) mkdirs() }

    fun saveKeystoreConfig(context: Context, cfg: CustomKeystoreConfig?) {
        val prefs = context.getSharedPreferences("CodeStudioKeystorePrefs", Context.MODE_PRIVATE)
        if (cfg == null) {
            prefs.edit().clear().apply()
        } else {
            prefs.edit()
                .putString("ks_path", cfg.keystoreFile.absolutePath)
                .putString("ks_pass", cfg.keystorePassword)
                .putString("key_alias", cfg.keyAlias)
                .putString("key_pass", cfg.keyPassword)
                .apply()
        }
    }

    fun loadKeystoreConfig(context: Context): CustomKeystoreConfig? {
        val prefs = context.getSharedPreferences("CodeStudioKeystorePrefs", Context.MODE_PRIVATE)
        val path = prefs.getString("ks_path", "") ?: ""
        if (path.isBlank()) return null
        val file = File(path)
        if (!file.exists() || !file.isFile) return null
        return CustomKeystoreConfig(
            keystoreFile = file,
            keystorePassword = prefs.getString("ks_pass", "") ?: "",
            keyAlias = prefs.getString("key_alias", "") ?: "",
            keyPassword = prefs.getString("key_pass", "") ?: ""
        )
    }

    fun generateReleaseKeystore(
        context: Context,
        fileName: String,
        storePassword: String,
        keyAlias: String,
        keyPassword: String,
        commonName: String
    ): Pair<Boolean, CustomKeystoreConfig?> {
        return try {
            val cleanName = fileName.trim().ifEmpty { "release" }.let {
                if (it.endsWith(".p12", true) || it.endsWith(".keystore", true) || it.endsWith(".jks", true)) it else "$it.p12"
            }
            val outFile = File(getKeystoresDir(context), cleanName)
            val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val certDer = buildSelfSignedX509(keyPair.public.encoded, keyPair.private, commonName.ifBlank { "CodeStudio Release" })
            val cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate

            val ks = KeyStore.getInstance("PKCS12")
            ks.load(null, storePassword.toCharArray())
            ks.setKeyEntry(keyAlias, keyPair.private, keyPassword.toCharArray(), arrayOf(cert))
            FileOutputStream(outFile).use { fos ->
                ks.store(fos, storePassword.toCharArray())
            }
            val cfg = CustomKeystoreConfig(outFile, storePassword, keyAlias, keyPassword)
            saveKeystoreConfig(context, cfg)
            true to cfg
        } catch (e: Exception) {
            e.printStackTrace()
            false to null
        }
    }

    // ================= ৪. AAPT2 হেলথ ও SDK ডাউনলোড =================
    suspend fun inspectAapt2Health(context: Context): Aapt2HealthStatus = withContext(Dispatchers.IO) {
        val bin = Aapt2ResourcePipeline.resolveAapt2Binary(context)
            ?: return@withContext Aapt2HealthStatus(
                isAvailable = false,
                isExecutable = false,
                binaryPath = "",
                versionText = "",
                message = "libaapt2.so missing! Place ARM64 aapt2 binary at app/src/main/jniLibs/arm64-v8a/libaapt2.so and rebuild CodeStudio."
            )

        return@withContext try {
            val pb = ProcessBuilder(bin.absolutePath, "version")
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            val done = proc.waitFor(5, TimeUnit.SECONDS)
            if (!done) {
                proc.destroyForcibly()
                Aapt2HealthStatus(
                    isAvailable = true,
                    isExecutable = false,
                    binaryPath = bin.absolutePath,
                    versionText = "",
                    message = "AAPT2 timed out during self-test."
                )
            } else if (proc.exitValue() == 0) {
                Aapt2HealthStatus(
                    isAvailable = true,
                    isExecutable = true,
                    binaryPath = bin.absolutePath,
                    versionText = out.lines().firstOrNull() ?: "AAPT2 Ready",
                    message = "ARM64 AAPT2 binary verified and executable."
                )
            } else {
                Aapt2HealthStatus(
                    isAvailable = true,
                    isExecutable = false,
                    binaryPath = bin.absolutePath,
                    versionText = out,
                    message = "AAPT2 exited with code ${proc.exitValue()}: $out"
                )
            }
        } catch (e: Exception) {
            val raw = e.message ?: "Execution failed"
            val hint = if (raw.contains("Permission denied", ignoreCase = true) || raw.contains("error=13", ignoreCase = true)) {
                "SELinux W^X blocked execution from app data. Package binary inside app/src/main/jniLibs/arm64-v8a/libaapt2.so."
            } else {
                raw
            }
            Aapt2HealthStatus(
                isAvailable = true,
                isExecutable = false,
                binaryPath = bin.absolutePath,
                versionText = "",
                message = hint
            )
        }
    }

    suspend fun downloadPlatformSdk(
        context: Context,
        spec: SdkPlatformSpec,
        isCancelled: () -> Boolean,
        onProgress: (SdkDownloadProgress) -> Unit
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val targetJar = getAndroidJarFile(context, spec.apiLevel)
        val tmpJar = File(targetJar.parentFile, "android.jar.tmp")
        targetJar.parentFile?.mkdirs()

        var lastError = "Failed to connect to Google Android Repository."

        for (urlStr in spec.downloadUrls) {
            if (isCancelled()) {
                tmpJar.delete()
                return@withContext false to "Download cancelled."
            }
            var conn: HttpURLConnection? = null
            try {
                onProgress(SdkDownloadProgress(spec.apiLevel, 0L, 0L, "Connecting to Google SDK Repo..."))
                conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "CodeStudio-Android/2.0")
                    connectTimeout = 20_000
                    readTimeout = 60_000
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    lastError = "HTTP $code for $urlStr"
                    conn.disconnect()
                    continue
                }

                val totalLen = conn.contentLengthLong.coerceAtLeast(0L)
                val tracker = ByteTrackingInputStream(conn.inputStream)
                var extractedOk = false
                var lastEmitMs = 0L
                val buf = ByteArray(65_536)

                ZipInputStream(BufferedInputStream(tracker, 65_536)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (isCancelled()) {
                            tmpJar.delete()
                            return@withContext false to "Download cancelled."
                        }
                        val eName = entry.name.replace("\\", "/")
                        val now = System.currentTimeMillis()
                        if (now - lastEmitMs >= 150L) {
                            lastEmitMs = now
                            onProgress(
                                SdkDownloadProgress(
                                    apiLevel = spec.apiLevel,
                                    downloadedBytes = tracker.totalRead,
                                    totalBytes = totalLen,
                                    phase = "Scanning SDK archive..."
                                )
                            )
                        }

                        if (!entry.isDirectory && (eName == "android.jar" || eName.endsWith("/android.jar"))) {
                            BufferedOutputStream(FileOutputStream(tmpJar), 65_536).use { bos ->
                                var read: Int
                                while (zis.read(buf).also { read = it } > 0) {
                                    if (isCancelled()) {
                                        tmpJar.delete()
                                        return@withContext false to "Download cancelled."
                                    }
                                    bos.write(buf, 0, read)
                                    val tNow = System.currentTimeMillis()
                                    if (tNow - lastEmitMs >= 120L) {
                                        lastEmitMs = tNow
                                        onProgress(
                                            SdkDownloadProgress(
                                                apiLevel = spec.apiLevel,
                                                downloadedBytes = tracker.totalRead,
                                                totalBytes = totalLen,
                                                phase = "Extracting android.jar..."
                                            )
                                        )
                                    }
                                }
                            }
                            extractedOk = tmpJar.exists() && tmpJar.length() > 100_000L
                            break
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                conn.disconnect()

                if (extractedOk) {
                    if (targetJar.exists()) targetJar.delete()
                    if (!tmpJar.renameTo(targetJar)) {
                        tmpJar.copyTo(targetJar, overwrite = true)
                        tmpJar.delete()
                    }
                    return@withContext true to "Android SDK API ${spec.apiLevel} ready!"
                } else {
                    tmpJar.delete()
                    lastError = "android.jar was not found inside downloaded archive."
                }
            } catch (e: Exception) {
                tmpJar.delete()
                lastError = e.message ?: "Download error"
            } finally {
                try {
                    conn?.disconnect()
                } catch (_: Exception) { }
            }
        }

        false to lastError
    }

    suspend fun importAndroidJarFromUri(
        context: Context,
        uri: Uri,
        apiLevel: Int
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val targetJar = getAndroidJarFile(context, apiLevel)
        val tmpFile = File(targetJar.parentFile, "import.tmp")
        targetJar.parentFile?.mkdirs()

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedOutputStream(FileOutputStream(tmpFile), 65_536).use { bos ->
                    input.copyTo(bos)
                }
            } ?: return@withContext false to "Cannot open selected file."

            if (!tmpFile.exists() || tmpFile.length() < 100_000L) {
                tmpFile.delete()
                return@withContext false to "Selected file is too small or invalid."
            }

            var hasAndroidJarEntry = false
            var hasClassEntries = false
            ZipInputStream(BufferedInputStream(FileInputStream(tmpFile), 65_536)).use { zis ->
                var entry = zis.nextEntry
                var checked = 0
                while (entry != null && checked < 600) {
                    val name = entry.name.replace("\\", "/")
                    if (name == "android.jar" || name.endsWith("/android.jar")) {
                        hasAndroidJarEntry = true
                        break
                    }
                    if (name.endsWith(".class") || name == "android/app/Activity.class") {
                        hasClassEntries = true
                    }
                    checked++
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (hasAndroidJarEntry) {
                val outTmp = File(targetJar.parentFile, "android.jar.tmp")
                var found = false
                ZipInputStream(BufferedInputStream(FileInputStream(tmpFile), 65_536)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name.replace("\\", "/")
                        if (!entry.isDirectory && (name == "android.jar" || name.endsWith("/android.jar"))) {
                            BufferedOutputStream(FileOutputStream(outTmp), 65_536).use { bos ->
                                zis.copyTo(bos)
                            }
                            found = outTmp.exists() && outTmp.length() > 100_000L
                            break
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                tmpFile.delete()
                if (!found) {
                    outTmp.delete()
                    return@withContext false to "Could not extract android.jar from ZIP."
                }
                if (targetJar.exists()) targetJar.delete()
                outTmp.renameTo(targetJar)
                return@withContext true to "Extracted & installed android.jar for API $apiLevel!"
            } else if (hasClassEntries) {
                if (targetJar.exists()) targetJar.delete()
                tmpFile.renameTo(targetJar)
                return@withContext true to "Imported android.jar for API $apiLevel!"
            } else {
                tmpFile.delete()
                return@withContext false to "File does not look like a valid android.jar or SDK platform ZIP."
            }
        } catch (e: Exception) {
            tmpFile.delete()
            return@withContext false to "Import failed: ${e.message ?: "Invalid file"}"
        }
    }

    suspend fun getCacheStats(context: Context): EngineCacheStats = withContext(Dispatchers.IO) {
        val root = MavenDependencyResolver.getCacheRoot(context)
        EngineCacheStats(
            mavenRepoBytes = folderSize(File(root, "repo")),
            unpackedAarBytes = folderSize(File(root, "unpacked")),
            extDexBytes = folderSize(File(root, "ext-dex")),
            compiledResBytes = folderSize(File(root, "compiled-res"))
        )
    }

    suspend fun clearAllCaches(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = MavenDependencyResolver.getCacheRoot(context)
            listOf("repo", "unpacked", "ext-dex", "compiled-res", "graphs").forEach { sub ->
                val f = File(root, sub)
                if (f.exists()) deleteRecursivelySafe(f)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun folderSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { f ->
            if (f.isFile) total += f.length()
        }
        return total
    }

    private fun buildSelfSignedX509(publicKeyInfoDer: ByteArray, privateKey: PrivateKey, commonName: String): ByteArray {
        val algIdSha256WithRsa = derWrap(
            0x30,
            derWrap(0x06, byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B)),
            byteArrayOf(0x05, 0x00)
        )
        val cnAttr = derWrap(
            0x30,
            derWrap(0x06, byteArrayOf(0x55, 0x04, 0x03)),
            derWrap(0x0C, commonName.toByteArray(Charsets.UTF_8))
        )
        val nameSeq = derWrap(0x30, derWrap(0x31, cnAttr))
        val validity = derWrap(
            0x30,
            derWrap(0x17, "250101000000Z".toByteArray(Charsets.US_ASCII)),
            derWrap(0x17, "491231235959Z".toByteArray(Charsets.US_ASCII))
        )
        val tbsCert = derWrap(
            0x30,
            derWrap(0xA0, derWrap(0x02, byteArrayOf(0x02))),
            derWrap(0x02, byteArrayOf(0x01)),
            algIdSha256WithRsa,
            nameSeq,
            validity,
            nameSeq,
            publicKeyInfoDer
        )
        val sigBytes = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(tbsCert)
            sign()
        }
        return derWrap(0x30, tbsCert, algIdSha256WithRsa, derWrap(0x03, byteArrayOf(0x00), sigBytes))
    }

    private fun derWrap(tag: Int, vararg parts: ByteArray): ByteArray {
        val bodyLen = parts.sumOf { it.size }
        val baos = ByteArrayOutputStream(bodyLen + 8)
        baos.write(tag)
        when {
            bodyLen < 128 -> baos.write(bodyLen)
            bodyLen < 256 -> { baos.write(0x81); baos.write(bodyLen) }
            bodyLen < 65536 -> { baos.write(0x82); baos.write(bodyLen shr 8); baos.write(bodyLen and 0xFF) }
            else -> {
                baos.write(0x83)
                baos.write((bodyLen shr 16) and 0xFF)
                baos.write((bodyLen shr 8) and 0xFF)
                baos.write(bodyLen and 0xFF)
            }
        }
        for (p in parts) baos.write(p)
        return baos.toByteArray()
    }
}