package com.shd.codestudio.nativeengine

import android.content.Context
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.Diagnostic
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.OutputMode
import com.android.tools.r8.R8
import com.android.tools.r8.R8Command
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class DexPipelineResult(
    val success: Boolean,
    val dexFiles: List<File>,
    val diagnostics: List<BuildDiagnosticItem> = emptyList(),
    val errorMessage: String = ""
)

object D8DexPipeline {

    private fun getExtDexCacheDir(context: Context): File =
        File(MavenDependencyResolver.getCacheRoot(context), "ext-dex").apply { if (!exists()) mkdirs() }

    fun compileToDex(
        context: Context,
        module: ModuleConfig,
        variant: EngineBuildVariant,
        classesDirs: List<File>,
        libraryJars: List<File>,
        libraries: List<ResolvedLibrary>,
        androidJar: File,
        workDir: File,
        isCancelled: () -> Boolean = { false },
        onLog: (String) -> Unit
    ): DexPipelineResult {
        val finalDexDir = File(workDir, "intermediates/dex_out").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        val appClassPaths = ArrayList<Path>()
        for (dir in classesDirs) {
            if (!dir.exists()) continue
            dir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".class") }
                .forEach { appClassPaths.add(it.toPath()) }
        }

        val rawJars = (libraryJars + module.localJarFiles)
            .filter { it.exists() && it.length() > 0L }
            .distinctBy { it.absolutePath }

        val uniqueJars = prepareDeduplicatedJars(
            appClassDirs = classesDirs,
            rawJars = rawJars,
            workDir = workDir,
            onLog = onLog
        )

        if (appClassPaths.isEmpty() && uniqueJars.isEmpty()) {
            return DexPipelineResult(false, emptyList(), errorMessage = "No .class files or libraries found to dex.")
        }

        val diagnostics = ArrayList<BuildDiagnosticItem>()
        val diagHandler = object : DiagnosticsHandler {
            override fun error(error: Diagnostic) {
                val msg = error.diagnosticMessage ?: "D8/R8 error"
                diagnostics.add(BuildDiagnosticItem("D8/R8", 0, 0, msg, isError = true))
                onLog("  ERROR (D8/R8): $msg")
            }

            override fun warning(warning: Diagnostic) {
                val msg = warning.diagnosticMessage ?: return
                diagnostics.add(BuildDiagnosticItem("D8/R8", 0, 0, msg, isError = false))
            }

            override fun info(info: Diagnostic) { }
        }

        return try {
            if (variant == EngineBuildVariant.RELEASE) {
                runReleaseR8(
                    module = module,
                    appClassPaths = appClassPaths,
                    libraryJars = uniqueJars,
                    libraries = libraries,
                    androidJar = androidJar,
                    finalDexDir = finalDexDir,
                    workDir = workDir,
                    diagHandler = diagHandler,
                    onLog = onLog
                )
            } else {
                runDebugIncrementalD8(
                    context = context,
                    minSdk = module.minSdk,
                    appClassPaths = appClassPaths,
                    libraryJars = uniqueJars,
                    androidJar = androidJar,
                    finalDexDir = finalDexDir,
                    workDir = workDir,
                    diagHandler = diagHandler,
                    isCancelled = isCancelled,
                    onLog = onLog
                )
            }

            val dexOut = finalDexDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".dex") }
                ?.sortedBy { it.name }
                ?: emptyList()

            if (dexOut.isEmpty()) {
                DexPipelineResult(false, emptyList(), diagnostics, "D8/R8 produced no .dex files.")
            } else {
                DexPipelineResult(true, dexOut, diagnostics)
            }
        } catch (e: Exception) {
            val msg = e.message ?: "D8/R8 dexing failed"
            onLog("  ERROR: $msg")
            DexPipelineResult(false, emptyList(), diagnostics, msg)
        }
    }

    private fun prepareDeduplicatedJars(
        appClassDirs: List<File>,
        rawJars: List<File>,
        workDir: File,
        onLog: (String) -> Unit
    ): List<File> {
        val seenClasses = HashSet<String>(16_384)
        for (dir in appClassDirs) {
            if (!dir.exists()) continue
            val baseLen = dir.absolutePath.length + 1
            dir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".class") }
                .forEach { f ->
                    val rel = f.absolutePath.substring(baseLen).replace('\\', '/')
                    seenClasses.add(rel)
                }
        }

        // kotlin-stdlib (1.8+) অবশ্যই kotlin-stdlib-jdk7 / jdk8-এর আগে প্রসেস করা
        val orderedJars = rawJars.sortedWith(
            compareBy<File> { jar ->
                val n = jar.name.lowercase()
                when {
                    n.startsWith("kotlin-stdlib-jdk") -> 2
                    n.startsWith("kotlin-stdlib-common") -> 3
                    n.startsWith("kotlin-stdlib") -> 0
                    else -> 1
                }
            }
        )

        val dedupDir = File(workDir, "intermediates/dedup_jars").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        val result = ArrayList<File>(orderedJars.size)
        var skippedCount = 0
        var strippedCount = 0

        for ((idx, jar) in orderedJars.withIndex()) {
            val jarClasses = ArrayList<String>()
            var dupCount = 0
            try {
                ZipInputStream(BufferedInputStream(FileInputStream(jar), 32_768)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val eName = entry.name.replace('\\', '/')
                        if (!entry.isDirectory &&
                            eName.endsWith(".class") &&
                            eName != "module-info.class" &&
                            !eName.startsWith("META-INF/")
                        ) {
                            jarClasses.add(eName)
                            if (eName in seenClasses) dupCount++
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } catch (_: Exception) {
                continue
            }

            if (jarClasses.isEmpty()) {
                continue
            }

            if (dupCount == jarClasses.size) {
                skippedCount++
                continue
            }

            if (dupCount == 0) {
                seenClasses.addAll(jarClasses)
                result.add(jar)
            } else {
                val cleanJar = File(dedupDir, "${idx}_${jar.name}")
                val buf = ByteArray(32_768)
                var keptAnyClass = false
                try {
                    ZipInputStream(BufferedInputStream(FileInputStream(jar), 32_768)).use { zis ->
                        ZipOutputStream(BufferedOutputStream(FileOutputStream(cleanJar), 32_768)).use { zos ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                val eName = entry.name.replace('\\', '/')
                                val isClass = eName.endsWith(".class") && eName != "module-info.class" && !eName.startsWith("META-INF/")
                                val shouldSkip = entry.isDirectory || (isClass && eName in seenClasses)
                                if (!shouldSkip) {
                                    if (isClass) {
                                        seenClasses.add(eName)
                                        keptAnyClass = true
                                    }
                                    zos.putNextEntry(ZipEntry(eName))
                                    var r: Int
                                    while (zis.read(buf).also { r = it } > 0) {
                                        zos.write(buf, 0, r)
                                    }
                                    zos.closeEntry()
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                    }
                    if (keptAnyClass && cleanJar.exists() && cleanJar.length() > 0L) {
                        strippedCount++
                        result.add(cleanJar)
                    } else {
                        cleanJar.delete()
                    }
                } catch (_: Exception) {
                    cleanJar.delete()
                }
            }
        }

        if (skippedCount > 0 || strippedCount > 0) {
            onLog("  Deduplicated classpath ($skippedCount redundant JAR skipped, $strippedCount trimmed)")
        }

        return result
    }

    private fun runDebugIncrementalD8(
        context: Context,
        minSdk: Int,
        appClassPaths: List<Path>,
        libraryJars: List<File>,
        androidJar: File,
        finalDexDir: File,
        workDir: File,
        diagHandler: DiagnosticsHandler,
        isCancelled: () -> Boolean,
        onLog: (String) -> Unit
    ) {
        val extDexCache = getExtDexCacheDir(context)
        val allJarPaths = libraryJars.map { it.toPath() }
        val mergeInputs = ArrayList<Path>()

        var newlyDexedLibs = 0
        for (jar in libraryJars) {
            if (isCancelled()) throw IllegalStateException("Cancelled")
            val cacheKey = buildJarCacheKey(jar, minSdk)
            val cachedDexZip = File(extDexCache, "$cacheKey.dex.zip")
            val okMarker = File(extDexCache, "$cacheKey.ok")

            if (!okMarker.exists() || !cachedDexZip.exists() || cachedDexZip.length() == 0L) {
                if (cachedDexZip.exists()) cachedDexZip.delete()
                if (newlyDexedLibs == 0) {
                    onLog("  Pre-dexing external libraries (cached for future builds)...")
                }
                newlyDexedLibs++
                val jarPath = jar.toPath()
                val otherClasspath = allJarPaths.filter { it != jarPath }
                val libCmd = D8Command.builder(diagHandler)
                    .setMode(CompilationMode.RELEASE)
                    .setMinApiLevel(minSdk)
                    .setIntermediate(true)
                    .addLibraryFiles(androidJar.toPath())
                    .addClasspathFiles(otherClasspath)
                    .addProgramFiles(jarPath)
                    .setOutput(cachedDexZip.toPath(), OutputMode.DexIndexed)
                    .build()
                D8.run(libCmd)
                if (cachedDexZip.exists() && cachedDexZip.length() > 0L) {
                    okMarker.writeText("ok")
                }
            }
            if (cachedDexZip.exists() && cachedDexZip.length() > 0L) {
                mergeInputs.add(cachedDexZip.toPath())
            }
        }
        if (libraryJars.isNotEmpty() && newlyDexedLibs == 0) {
            onLog("  ExtDex cache hit (${libraryJars.size} libraries skipped in 0ms)")
        }

        if (appClassPaths.isNotEmpty()) {
            val appDexZip = File(workDir, "intermediates/app_dex.zip").apply {
                parentFile?.mkdirs()
                if (exists()) delete()
            }
            val appCmd = D8Command.builder(diagHandler)
                .setMode(CompilationMode.DEBUG)
                .setMinApiLevel(minSdk)
                .setIntermediate(true)
                .addLibraryFiles(androidJar.toPath())
                .addClasspathFiles(allJarPaths)
                .addProgramFiles(appClassPaths)
                .setOutput(appDexZip.toPath(), OutputMode.DexIndexed)
                .build()
            D8.run(appCmd)
            if (appDexZip.exists() && appDexZip.length() > 0L) {
                mergeInputs.add(appDexZip.toPath())
            }
        }

        if (mergeInputs.isNotEmpty()) {
            val mergeCmd = D8Command.builder(diagHandler)
                .setMode(CompilationMode.DEBUG)
                .setMinApiLevel(minSdk)
                .setIntermediate(false)
                .addLibraryFiles(androidJar.toPath())
                .addProgramFiles(mergeInputs)
                .setOutput(finalDexDir.toPath(), OutputMode.DexIndexed)
                .build()
            D8.run(mergeCmd)
        }
    }

    private fun runReleaseR8(
        module: ModuleConfig,
        appClassPaths: List<Path>,
        libraryJars: List<File>,
        libraries: List<ResolvedLibrary>,
        androidJar: File,
        finalDexDir: File,
        workDir: File,
        diagHandler: DiagnosticsHandler,
        onLog: (String) -> Unit
    ) {
        onLog("  Running Google R8 shrinking & optimization...")
        val defaultRulesFile = File(workDir, "intermediates/r8_default_rules.pro").apply {
            parentFile?.mkdirs()
            writeText(
                """
                -dontwarn **
                -ignorewarnings
                -keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
                -keep class ${module.namespace}.** { *; }
                -keep class ${module.applicationId}.** { *; }
                -keep public class * extends android.app.Application
                -keep public class * extends android.app.Activity
                -keep public class * extends android.app.Service
                -keep public class * extends android.content.BroadcastReceiver
                -keep public class * extends android.content.ContentProvider
                -keepclassmembers class * extends android.app.Activity {
                    public void *(android.view.View);
                }
                """.trimIndent() + "\n"
            )
        }

        val proguardPaths = ArrayList<Path>()
        proguardPaths.add(defaultRulesFile.toPath())
        module.proguardFiles.filter { it.exists() }.forEach { proguardPaths.add(it.toPath()) }
        libraries.mapNotNull { it.proguardFile }.filter { it.exists() }.forEach { proguardPaths.add(it.toPath()) }

        val r8Builder = R8Command.builder(diagHandler)
            .setMode(CompilationMode.RELEASE)
            .setMinApiLevel(module.minSdk)
            .addLibraryFiles(androidJar.toPath())
            .addProgramFiles(appClassPaths)
            .addProgramFiles(libraryJars.map { it.toPath() })
            .addProguardConfigurationFiles(proguardPaths)
            .setOutput(finalDexDir.toPath(), OutputMode.DexIndexed)

        R8.run(r8Builder.build())
    }

    private fun buildJarCacheKey(jar: File, minSdk: Int): String {
        val isTempDedup = jar.parentFile?.name == "dedup_jars"
        val raw = if (isTempDedup) {
            "${jar.name}:${jar.length()}:api$minSdk"
        } else {
            "${jar.absolutePath}:${jar.length()}:${jar.lastModified()}:api$minSdk"
        }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
        val cleanName = jar.nameWithoutExtension.replace(Regex("[^A-Za-z0-9._-]"), "_").take(36)
        return "${cleanName}_$hash"
    }
}