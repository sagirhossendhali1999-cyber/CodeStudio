package com.shd.codestudio.nativeengine

import android.content.Context
import dalvik.system.DexClassLoader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader

data class KotlinCompileResult(
    val success: Boolean,
    val classesDir: File,
    val kotlinFileCount: Int,
    val compiledClassCount: Int,
    val extraRuntimeJars: List<File> = emptyList(),
    val diagnostics: List<BuildDiagnosticItem> = emptyList(),
    val errorMessage: String = ""
)

object KotlinNativeCompiler {

    private val KOTLINC_DIAG_REGEX = Regex(
        """^(e|w|v|i):\s*(?:file://)?(.+?\.kt):(\d+):(\d+):\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val KOTLINC_ALT_DIAG_REGEX = Regex(
        """^(e|w):\s*(?:file://)?(.+?\.kt):\s*\((\d+),\s*(\d+)\):\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )

    @Volatile
    private var cachedDexLoaderKey: String = ""
    @Volatile
    private var cachedDexLoader: DexClassLoader? = null

    fun collectKotlinFiles(roots: List<File>): List<File> {
        val seen = HashSet<String>()
        val result = ArrayList<File>()
        for (root in roots) {
            if (!root.exists()) continue
            root.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".kt", true) }
                .forEach { f ->
                    if (seen.add(f.absolutePath)) {
                        result.add(f)
                    }
                }
        }
        return result
    }

    fun hasKotlinSources(module: ModuleConfig, extraSourceDirs: List<File> = emptyList()): Boolean =
        collectKotlinFiles(module.sourceDirs + extraSourceDirs).isNotEmpty()

    fun compile(
        context: Context,
        module: ModuleConfig,
        generatedJavaDir: File,
        classpathJars: List<File>,
        androidJar: File,
        classesOutDir: File,
        workDir: File,
        extraSourceDirs: List<File> = emptyList(),
        onLog: (String) -> Unit
    ): KotlinCompileResult {
        if (classesOutDir.exists()) {
            classesOutDir.deleteRecursively()
        }
        classesOutDir.mkdirs()

        val ktFiles = collectKotlinFiles(module.sourceDirs + extraSourceDirs)
        if (ktFiles.isEmpty()) {
            return KotlinCompileResult(
                success = true,
                classesDir = classesOutDir,
                kotlinFileCount = 0,
                compiledClassCount = 0
            )
        }

        if (!SdkEngineManager.isKotlincInstalled(context)) {
            return KotlinCompileResult(
                success = false,
                classesDir = classesOutDir,
                kotlinFileCount = ktFiles.size,
                compiledClassCount = 0,
                errorMessage = "Kotlin + Compose Compiler Pack could not be initialized. Check internet connection for first-time auto-setup."
            )
        }

        val dexLoader = getOrCreateKotlincClassLoader(context)
            ?: return KotlinCompileResult(
                success = false,
                classesDir = classesOutDir,
                kotlinFileCount = ktFiles.size,
                compiledClassCount = 0,
                errorMessage = "Failed to initialize Kotlin compiler DexClassLoader."
            )

        val javaSourceFiles = collectJavaFiles(module.sourceDirs + listOf(generatedJavaDir) + extraSourceDirs)

        // যদি Maven ডিপেন্ডেন্সিতে kotlin-stdlib না থাকে (যেমন একদম বেসিক/অফলাইন Kotlin প্রজেক্ট), তবে বান্ডেলড stdlib যোগ করা
        val extraRuntimeJars = mutableListOf<File>()
        val hasStdlibInClasspath = classpathJars.any {
            val n = it.name.lowercase()
            n.startsWith("kotlin-stdlib") && !n.startsWith("kotlin-stdlib-common")
        }
        if (!hasStdlibInClasspath) {
            val bundledStdlib = SdkEngineManager.getBundledKotlinStdlibJar(context)
            if (bundledStdlib.exists() && bundledStdlib.length() > 0L) {
                extraRuntimeJars.add(bundledStdlib)
                onLog("  Using built-in kotlin-stdlib (${bundledStdlib.name})")
            }
        }

        val cpList = buildList {
            add(androidJar.absolutePath)
            add(classesOutDir.absolutePath)
            classpathJars
                .filter { it.exists() && (it.isDirectory || it.length() > 0L) }
                .forEach { add(it.absolutePath) }
            extraRuntimeJars.forEach { add(it.absolutePath) }
            module.localJarFiles
                .filter { it.exists() && it.length() > 0L }
                .forEach { add(it.absolutePath) }
        }

        val jvmTarget = when {
            module.javaVersion <= 8 -> "1.8"
            module.javaVersion >= 17 -> "17"
            else -> module.javaVersion.toString()
        }
        val cleanModName = module.moduleName.removePrefix(":").replace(":", "_").ifBlank { "app" }

        val args = ArrayList<String>().apply {
            ktFiles.forEach { add(it.absolutePath) }
            javaSourceFiles.forEach { add(it.absolutePath) }
            add("-d")
            add(classesOutDir.absolutePath)
            add("-module-name")
            add(cleanModName)
            add("-jvm-target")
            add(jvmTarget)
            add("-no-stdlib")
            add("-no-reflect")
            add("-no-jdk")
            if (cpList.isNotEmpty()) {
                add("-classpath")
                add(cpList.joinToString(File.pathSeparator))
            }

            if (module.composeEnabled) {
                val composeJar = SdkEngineManager.getComposePluginJar(context)
                if (composeJar.exists() && composeJar.length() > 0L) {
                    runCatching { composeJar.setReadOnly() }
                    add("-Xplugin=${composeJar.absolutePath}")
                    onLog("  Compose Compiler Plugin enabled")
                } else {
                    onLog("  WARN: Compose plugin descriptor not found; compiling Kotlin without Compose IR plugin.")
                }
            }
        }

        onLog("  Compiling ${ktFiles.size} Kotlin file(s) (JVM $jvmTarget)...")

        val tmpDir = File(workDir, "intermediates/kotlinc_tmp").apply { mkdirs() }
        val prevTmp = System.getProperty("java.io.tmpdir")
        val prevKeepAlive = System.getProperty("kotlin.environment.keepalive")
        val prevDaemon = System.getProperty("kotlin.daemon.enabled")
        val prevThreadLoader = Thread.currentThread().contextClassLoader

        val outBaos = ByteArrayOutputStream()
        val ps = PrintStream(outBaos, true, Charsets.UTF_8.name())

        var exitName = "INTERNAL_ERROR"
        var invokeError: String? = null

        try {
            System.setProperty("java.io.tmpdir", tmpDir.absolutePath)
            System.setProperty("kotlin.environment.keepalive", "false")
            System.setProperty("kotlin.daemon.enabled", "false")
            System.setProperty("idea.use.native.fs.for.win", "false")
            Thread.currentThread().contextClassLoader = dexLoader

            val compilerCls = dexLoader.loadClass("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
            val compilerInstance = compilerCls.getDeclaredConstructor().newInstance()
            val execMethod = compilerCls.getMethod(
                "exec",
                PrintStream::class.java,
                Array<String>::class.java
            )
            val exitObj = execMethod.invoke(compilerInstance, ps, args.toTypedArray())
            ps.flush()
            exitName = exitObj?.toString() ?: "INTERNAL_ERROR"
        } catch (t: Throwable) {
            val cause = t.cause ?: t
            cause.printStackTrace()
            invokeError = "Kotlin Compiler Error (${cause.javaClass.simpleName}): ${cause.message ?: "Invocation failed"}"
        } finally {
            Thread.currentThread().contextClassLoader = prevThreadLoader
            if (prevTmp != null) System.setProperty("java.io.tmpdir", prevTmp)
            if (prevKeepAlive != null) System.setProperty("kotlin.environment.keepalive", prevKeepAlive)
            if (prevDaemon != null) System.setProperty("kotlin.daemon.enabled", prevDaemon)
        }

        val rawOutput = outBaos.toString(Charsets.UTF_8.name()).trim()
        val diagnostics = parseKotlincOutput(rawOutput, module.moduleDir)

        if (invokeError != null) {
            onLog("  ERROR: $invokeError")
            diagnostics.addErrorIfMissing(invokeError)
            return KotlinCompileResult(
                success = false,
                classesDir = classesOutDir,
                kotlinFileCount = ktFiles.size,
                compiledClassCount = 0,
                extraRuntimeJars = extraRuntimeJars,
                diagnostics = diagnostics,
                errorMessage = invokeError
            )
        }

        val ok = exitName.equals("OK", ignoreCase = true)
        if (!ok) {
            rawOutput.lines()
                .filter { it.isNotBlank() }
                .take(45)
                .forEach { onLog("  KOTLINC: $it") }
            val firstErr = diagnostics.firstOrNull { it.isError }?.message
                ?: rawOutput.lines().firstOrNull { it.isNotBlank() }
                ?: "Kotlin compilation failed ($exitName)"
            return KotlinCompileResult(
                success = false,
                classesDir = classesOutDir,
                kotlinFileCount = ktFiles.size,
                compiledClassCount = 0,
                extraRuntimeJars = extraRuntimeJars,
                diagnostics = diagnostics,
                errorMessage = firstErr
            )
        }

        val compiledCount = classesOutDir.walkTopDown().count { it.isFile && it.name.endsWith(".class") }
        return KotlinCompileResult(
            success = true,
            classesDir = classesOutDir,
            kotlinFileCount = ktFiles.size,
            compiledClassCount = compiledCount,
            extraRuntimeJars = extraRuntimeJars,
            diagnostics = diagnostics
        )
    }

    fun invalidateCachedLoader() {
        cachedDexLoader = null
        cachedDexLoaderKey = ""
    }

    private fun getOrCreateKotlincClassLoader(context: Context): DexClassLoader? {
        val allFiles = SdkEngineManager.getKotlincDexDir(context)
            .listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".zip") || it.name.endsWith(".dex") || it.name.endsWith(".jar")) && it.length() > 0L }
            ?.sortedBy { it.name }
            ?: emptyList()

        if (allFiles.isEmpty()) return null

        // Android 14+ (API 34+) Security Policy: DexClassLoader ফাইলের ওপর read-only বাধ্যতামূলক
        allFiles.forEach { runCatching { it.setReadOnly() } }

        val resourceFiles = allFiles.filter { it.name.equals("kotlinc_resources.zip", ignoreCase = true) }
        val dexFiles = allFiles.filterNot { it.name.equals("kotlinc_resources.zip", ignoreCase = true) }
        if (dexFiles.isEmpty()) return null

        val key = allFiles.joinToString("|") { "${it.name}:${it.length()}:${it.lastModified()}" }

        cachedDexLoader?.let { existing ->
            if (cachedDexLoaderKey == key) return existing
        }

        return try {
            // kotlinc_resources.zip-কে parent URLClassLoader হিসেবে রাখলে ART কোনো "no classes.dex" ওয়ার্নিং ছাড়াই সব রিসোর্স খুঁজে পায়
            val parentLoader: ClassLoader = if (resourceFiles.isNotEmpty()) {
                URLClassLoader(
                    resourceFiles.map { it.toURI().toURL() }.toTypedArray(),
                    ClassLoader.getSystemClassLoader()
                )
            } else {
                ClassLoader.getSystemClassLoader()
            }

            val dexPath = dexFiles.joinToString(File.pathSeparator) { it.absolutePath }
            val optDir = File(SdkEngineManager.getKotlincDir(context), "oat").apply { mkdirs() }
            val loader = DexClassLoader(
                dexPath,
                optDir.absolutePath,
                null,
                parentLoader
            )
            cachedDexLoader = loader
            cachedDexLoaderKey = key
            loader
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    private fun collectJavaFiles(roots: List<File>): List<File> {
        val seen = HashSet<String>()
        val result = ArrayList<File>()
        for (root in roots) {
            if (!root.exists()) continue
            root.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".java", ignoreCase = true) }
                .forEach { f ->
                    if (seen.add(f.absolutePath)) {
                        result.add(f)
                    }
                }
        }
        return result
    }

    private fun parseKotlincOutput(output: String, moduleDir: File): MutableList<BuildDiagnosticItem> {
        val list = mutableListOf<BuildDiagnosticItem>()
        val prefix = moduleDir.absolutePath + "/"
        output.lines().forEach { rawLine ->
            val line = rawLine.trim()
            val m1 = KOTLINC_DIAG_REGEX.find(line)
            if (m1 != null) {
                val sev = m1.groupValues[1].lowercase()
                val path = m1.groupValues[2].removePrefix(prefix)
                val ln = m1.groupValues[3].toIntOrNull() ?: 0
                val col = m1.groupValues[4].toIntOrNull() ?: 0
                val msg = m1.groupValues[5]
                list.add(BuildDiagnosticItem(path, ln, col, msg, isError = sev == "e"))
                return@forEach
            }
            val m2 = KOTLINC_ALT_DIAG_REGEX.find(line)
            if (m2 != null) {
                val sev = m2.groupValues[1].lowercase()
                val path = m2.groupValues[2].removePrefix(prefix)
                val ln = m2.groupValues[3].toIntOrNull() ?: 0
                val col = m2.groupValues[4].toIntOrNull() ?: 0
                val msg = m2.groupValues[5]
                list.add(BuildDiagnosticItem(path, ln, col, msg, isError = sev == "e"))
            }
        }
        return list
    }

    private fun MutableList<BuildDiagnosticItem>.addErrorIfMissing(msg: String) {
        if (none { it.isError }) {
            add(BuildDiagnosticItem("kotlinc", 0, 0, msg, isError = true))
        }
    }
}