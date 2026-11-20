package com.shd.codestudio.nativeengine

import android.content.Context
import android.os.Build
import com.android.apksig.ApkSigner
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class CustomKeystoreConfig(
    val keystoreFile: File,
    val keystorePassword: String,
    val keyAlias: String,
    val keyPassword: String
)

data class ApkPackageResult(
    val success: Boolean,
    val signedApkFile: File,
    val errorMessage: String = ""
)

private class ByteCountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
    var bytesWritten: Long = 0L
        private set

    override fun write(b: Int) {
        out.write(b)
        bytesWritten++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        bytesWritten += len
    }
}

object ApkPackagerAndSigner {

    private val IGNORED_JAR_PREFIXES = listOf("META-INF/")
    private val MEDIA_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "mp3", "ogg", "wav", "mp4", "ttf", "otf", "woff")

    fun packageAndSign(
        context: Context,
        module: ModuleConfig,
        resourcesApFile: File,
        dexFiles: List<File>,
        libraries: List<ResolvedLibrary>,
        libraryJars: List<File>,
        workDir: File,
        outputApkFile: File,
        customKeystore: CustomKeystoreConfig? = null,
        onLog: (String) -> Unit
    ): ApkPackageResult {
        if (!resourcesApFile.exists()) {
            return ApkPackageResult(false, outputApkFile, "Missing resources.ap_ from AAPT2")
        }
        if (dexFiles.isEmpty()) {
            return ApkPackageResult(false, outputApkFile, "No classes.dex files found")
        }

        val unsignedApk = File(workDir, "intermediates/app-unsigned.apk").apply {
            parentFile?.mkdirs()
            if (exists()) delete()
        }
        outputApkFile.parentFile?.mkdirs()
        if (outputApkFile.exists()) outputApkFile.delete()

        return try {
            val writtenEntries = HashSet<String>()
            val mergedServiceFiles = LinkedHashMap<String, LinkedHashSet<String>>()
            val countingOut = ByteCountingOutputStream(BufferedOutputStream(FileOutputStream(unsignedApk), 65_536))

            ZipOutputStream(countingOut).use { zos ->
                // ১. AAPT2 resources.ap_ থেকে AndroidManifest.xml, resources.arsc ও res/ কপি করা
                ZipInputStream(BufferedInputStream(FileInputStream(resourcesApFile), 65_536)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory && writtenEntries.add(name)) {
                            val bytes = zis.readBytes()
                            val ext = name.substringAfterLast('.', "").lowercase()
                            val mustStore = name == "resources.arsc" || entry.method == ZipEntry.STORED || ext in MEDIA_EXTENSIONS
                            if (mustStore) {
                                writeStoredAlignedEntry(zos, countingOut, name, bytes, alignBytes = 4)
                            } else {
                                writeDeflatedEntry(zos, name, bytes)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                // ২. classes*.dex ফাইল যুক্ত করা
                for (dex in dexFiles) {
                    val name = dex.name
                    if (writtenEntries.add(name)) {
                        writeDeflatedEntry(zos, name, dex.readBytes())
                    }
                }

                // ৩. Native .so লাইব্রেরিসমূহ (১৬KB পেজ অ্যালাইনমেন্ট ও STORED মোড)
                val supportedAbis = Build.SUPPORTED_ABIS.toSet()
                val jniRoots = buildList {
                    val appJni = File(module.moduleDir, "src/main/jniLibs")
                    if (appJni.exists() && appJni.isDirectory) add(appJni)
                    libraries.mapNotNull { it.jniDir }.filter { it.exists() }.forEach { add(it) }
                }
                for (jniRoot in jniRoots) {
                    jniRoot.listFiles()?.filter { it.isDirectory }?.forEach { abiDir ->
                        val abi = abiDir.name
                        if (supportedAbis.isEmpty() || abi in supportedAbis || abi == "arm64-v8a") {
                            abiDir.listFiles()?.filter { it.isFile && it.name.endsWith(".so") }?.forEach { soFile ->
                                val zipPath = "lib/$abi/${soFile.name}"
                                if (writtenEntries.add(zipPath)) {
                                    writeStoredAlignedEntry(zos, countingOut, zipPath, soFile.readBytes(), alignBytes = 16_384)
                                }
                            }
                        }
                    }
                }

                // ৪. Java JAR-এর ভেতরের নন-ক্লাস রিসোর্স ও META-INF/services মার্জ করা
                for (jar in libraryJars + module.localJarFiles) {
                    if (!jar.exists() || jar.length() == 0L) continue
                    try {
                        ZipInputStream(BufferedInputStream(FileInputStream(jar), 32_768)).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                val name = entry.name.replace('\\', '/')
                                if (!entry.isDirectory) {
                                    if (name.startsWith("META-INF/services/") && name.length > "META-INF/services/".length) {
                                        val content = String(zis.readBytes(), Charsets.UTF_8)
                                        val set = mergedServiceFiles.getOrPut(name) { LinkedHashSet() }
                                        content.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                                            set.add(it)
                                        }
                                    } else {
                                        val skip = name.endsWith(".class") ||
                                            name.endsWith(".dex") ||
                                            name == "module-info.class" ||
                                            name == "META-INF/MANIFEST.MF" ||
                                            name.endsWith(".SF", true) ||
                                            name.endsWith(".DSA", true) ||
                                            name.endsWith(".RSA", true) ||
                                            IGNORED_JAR_PREFIXES.any { name.startsWith(it) }

                                        if (!skip && writtenEntries.add(name)) {
                                            writeDeflatedEntry(zos, name, zis.readBytes())
                                        }
                                    }
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                    } catch (_: Exception) { }
                }

                // মার্জ করা META-INF/services/* ফাইল লেখা
                for ((serviceName, lines) in mergedServiceFiles) {
                    if (lines.isNotEmpty() && writtenEntries.add(serviceName)) {
                        val payload = (lines.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)
                        writeDeflatedEntry(zos, serviceName, payload)
                    }
                }
            }

            // ৫. Google apksig দিয়ে v1 + v2 + v3 APK সাইনিং
            val (privateKey, cert) = if (customKeystore != null) {
                loadCustomKeystore(customKeystore)
            } else {
                loadOrGenerateDebugCredentials(context)
            }

            val signerConfig = ApkSigner.SignerConfig.Builder(
                "CodeStudio",
                privateKey,
                listOf(cert)
            ).build()

            val apkSigner = ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(unsignedApk)
                .setOutputApk(outputApkFile)
                .setMinSdkVersion(module.minSdk)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()
            apkSigner.sign()

            onLog("  Signed APK (${outputApkFile.length() / 1024} KB): ${outputApkFile.name}")
            ApkPackageResult(true, outputApkFile)
        } catch (e: Exception) {
            val msg = e.message ?: "APK packaging/signing failed"
            onLog("  ERROR: $msg")
            ApkPackageResult(false, outputApkFile, msg)
        }
    }

    private fun writeDeflatedEntry(zos: ZipOutputStream, name: String, data: ByteArray) {
        val entry = ZipEntry(name).apply {
            method = ZipEntry.DEFLATED
        }
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }

    private fun writeStoredAlignedEntry(
        zos: ZipOutputStream,
        countingOut: ByteCountingOutputStream,
        name: String,
        data: ByteArray,
        alignBytes: Int
    ) {
        val crc = CRC32().apply { update(data) }.value
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val headerBaseLen = 30L + nameBytes.size
        val predictedDataOffset = countingOut.bytesWritten + headerBaseLen
        val rem = (predictedDataOffset % alignBytes).toInt()
        val padLen = if (rem == 0) 0 else (alignBytes - rem)

        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = data.size.toLong()
            compressedSize = data.size.toLong()
            this.crc = crc
            if (padLen > 0) {
                extra = ByteArray(padLen)
            }
        }
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }

    private fun loadCustomKeystore(cfg: CustomKeystoreConfig): Pair<PrivateKey, X509Certificate> {
        val ksType = if (cfg.keystoreFile.name.endsWith(".p12", true) || cfg.keystoreFile.name.endsWith(".pfx", true)) {
            "PKCS12"
        } else {
            KeyStore.getDefaultType()
        }
        val ks = KeyStore.getInstance(ksType)
        FileInputStream(cfg.keystoreFile).use { fis ->
            ks.load(fis, cfg.keystorePassword.toCharArray())
        }
        val key = ks.getKey(cfg.keyAlias, cfg.keyPassword.toCharArray()) as PrivateKey
        val cert = ks.getCertificate(cfg.keyAlias) as X509Certificate
        return key to cert
    }

    private fun loadOrGenerateDebugCredentials(context: Context): Pair<PrivateKey, X509Certificate> {
        val engineDir = File(context.filesDir, "CodeStudioEngine").apply { mkdirs() }
        val keyFile = File(engineDir, "debug_rsa.pk8")
        val certFile = File(engineDir, "debug_cert.der")

        if (keyFile.exists() && certFile.exists()) {
            try {
                val priv = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(ByteArrayInputStream(certFile.readBytes())) as X509Certificate
                return priv to cert
            } catch (_: Exception) { }
        }

        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val certDer = buildSelfSignedX509Der(keyPair.public.encoded, keyPair.private)
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate

        keyFile.writeBytes(keyPair.private.encoded)
        certFile.writeBytes(certDer)
        return keyPair.private to cert
    }

    private fun buildSelfSignedX509Der(publicKeyInfoDer: ByteArray, privateKey: PrivateKey): ByteArray {
        val algIdSha256WithRsa = derSequence(
            derOid(byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B)),
            byteArrayOf(0x05, 0x00)
        )
        val cnAttr = derSequence(
            derOid(byteArrayOf(0x55, 0x04, 0x03)),
            derUtf8String("CodeStudio Debug")
        )
        val nameSeq = derSequence(derSet(cnAttr))
        val validity = derSequence(
            derUtcTime("250101000000Z"),
            derUtcTime("491231235959Z")
        )
        val versionExplicit = derWrap(0xA0, derInteger(byteArrayOf(0x02)))
        val serial = derInteger(byteArrayOf(0x01))

        val tbsCert = derSequence(
            versionExplicit,
            serial,
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

        return derSequence(
            tbsCert,
            algIdSha256WithRsa,
            derBitString(sigBytes)
        )
    }

    private fun derSequence(vararg parts: ByteArray): ByteArray = derWrap(0x30, *parts)
    private fun derSet(vararg parts: ByteArray): ByteArray = derWrap(0x31, *parts)
    private fun derOid(oidBytes: ByteArray): ByteArray = derWrap(0x06, oidBytes)
    private fun derUtf8String(s: String): ByteArray = derWrap(0x0C, s.toByteArray(Charsets.UTF_8))
    private fun derUtcTime(s: String): ByteArray = derWrap(0x17, s.toByteArray(Charsets.US_ASCII))
    private fun derInteger(bytes: ByteArray): ByteArray = derWrap(0x02, bytes)
    private fun derBitString(bytes: ByteArray): ByteArray = derWrap(0x03, byteArrayOf(0x00), bytes)

    private fun derWrap(tag: Int, vararg parts: ByteArray): ByteArray {
        val bodyLen = parts.sumOf { it.size }
        val baos = ByteArrayOutputStream(bodyLen + 8)
        baos.write(tag)
        writeDerLength(baos, bodyLen)
        for (p in parts) baos.write(p)
        return baos.toByteArray()
    }

    private fun writeDerLength(out: ByteArrayOutputStream, len: Int) {
        when {
            len < 128 -> out.write(len)
            len < 256 -> { out.write(0x81); out.write(len) }
            len < 65536 -> { out.write(0x82); out.write(len shr 8); out.write(len and 0xFF) }
            else -> {
                out.write(0x83)
                out.write((len shr 16) and 0xFF)
                out.write((len shr 8) and 0xFF)
                out.write(len and 0xFF)
            }
        }
    }
}