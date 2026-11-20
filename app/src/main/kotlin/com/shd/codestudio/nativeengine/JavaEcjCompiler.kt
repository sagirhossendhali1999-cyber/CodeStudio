package com.shd.codestudio.nativeengine

import org.eclipse.jdt.core.compiler.batch.BatchCompiler
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

data class JavaCompileResult(
    val success: Boolean,
    val classesDir: File,
    val sourceFileCount: Int,
    val compiledClassCount: Int,
    val diagnostics: List<BuildDiagnosticItem> = emptyList(),
    val errorMessage: String = ""
)

object JavaEcjCompiler {

    private val DIAG_REGEX = Regex("""^(.+?\.java):(\d+):\s*(error|warning):\s*(.+)$""", RegexOption.IGNORE_CASE)

    fun compile(
        module: ModuleConfig,
        generatedJavaDir: File,
        classpathJars: List<File>,
        androidJar: File,
        classesOutDir: File,
        workDir: File,
        onLog: (String) -> Unit
    ): JavaCompileResult {
        return compile(
            module = module,
            generatedJavaDir = generatedJavaDir,
            classpathJars = classpathJars,
            androidJar = androidJar,
            classesOutDir = classesOutDir,
            workDir = workDir,
            extraSourceDirs = emptyList(),
            onLog = onLog
        )
    }

    fun compile(
        module: ModuleConfig,
        generatedJavaDir: File,
        classpathJars: List<File>,
        androidJar: File,
        classesOutDir: File,
        workDir: File,
        extraSourceDirs: List<File>,
        onLog: (String) -> Unit
    ): JavaCompileResult {
        if (classesOutDir.exists()) {
            classesOutDir.deleteRecursively()
        }
        classesOutDir.mkdirs()

        val javaFiles = collectJavaFiles(module.sourceDirs + listOf(generatedJavaDir) + extraSourceDirs)
        if (javaFiles.isEmpty()) {
            return JavaCompileResult(
                success = true,
                classesDir = classesOutDir,
                sourceFileCount = 0,
                compiledClassCount = 0
            )
        }

        val argListFile = File(workDir, "intermediates/java_sources.txt").apply {
            parentFile?.mkdirs()
            writeText(
                javaFiles.joinToString("\n") { "\"${it.absolutePath.replace("\\", "\\\\")}\"" } + "\n",
                Charsets.UTF_8
            )
        }

        // ডিরেক্টরি (যেমন kotlinc_classes) এবং JAR উভয়কেই নিরাপদে ক্লাসপাথে অন্তর্ভুক্ত করা
        val cpEntries = buildList {
            add(classesOutDir.absolutePath)
            classpathJars
                .filter { it.exists() && (it.isDirectory || it.length() > 0L) }
                .distinctBy { it.absolutePath }
                .forEach { add(it.absolutePath) }
            module.localJarFiles
                .filter { it.exists() && it.length() > 0L }
                .distinctBy { it.absolutePath }
                .forEach { add(it.absolutePath) }
        }

        val requestedLevel = when {
            module.javaVersion <= 8 -> "1.8"
            module.javaVersion >= 17 -> "17"
            else -> module.javaVersion.toString()
        }

        // ১. দ্রুত Android-native nb-javac দিয়ে চেষ্টা (Java 8 compatible কোডের জন্য অতি দ্রুত)
        var (ok, rawOutput) = if (hasNbJavacMain()) {
            onLog("  Compiling ${javaFiles.size} Java file(s) with Android-native javac...")
            invokeNbJavacReflectively(
                androidJar = androidJar,
                classpath = cpEntries,
                classesOutDir = classesOutDir,
                argListFile = argListFile
            )
        } else {
            false to ""
        }

        // ২. nb-javac অনুপলব্ধ হলে বা Java 11/17 ফিচারের কারণে ব্যর্থ হলে ECJ ফলব্যাক
        val shouldTryEcj = !ok && (
            rawOutput.isEmpty() ||
                rawOutput.contains("Javac Error", ignoreCase = true) ||
                rawOutput.contains("not supported in -source 8", ignoreCase = true) ||
                rawOutput.contains("invalid flag", ignoreCase = true) ||
                module.javaVersion > 8
            )

        if (shouldTryEcj) {
            if (classesOutDir.exists()) classesOutDir.deleteRecursively()
            classesOutDir.mkdirs()
            onLog("  Switching to ECJ compiler (Java $requestedLevel)...")
            val ecjAttempt = invokeEcj(
                sourceLevel = requestedLevel,
                androidJar = androidJar,
                classpath = cpEntries,
                classesOutDir = classesOutDir,
                argListFile = argListFile
            )
            if (ecjAttempt.first || ecjAttempt.second.isNotBlank()) {
                ok = ecjAttempt.first
                rawOutput = ecjAttempt.second
            }
        }

        val diagnostics = parseCompilerOutput(rawOutput, module.moduleDir)
        if (!ok) {
            rawOutput.lines()
                .filter { it.isNotBlank() }
                .take(40)
                .forEach { onLog("  JAVA: $it") }
            return JavaCompileResult(
                success = false,
                classesDir = classesOutDir,
                sourceFileCount = javaFiles.size,
                compiledClassCount = 0,
                diagnostics = diagnostics,
                errorMessage = diagnostics.firstOrNull { it.isError }?.message
                    ?: "Java compilation failed: ${rawOutput.lines().firstOrNull() ?: "Unknown error"}"
            )
        }

        val compiledCount = classesOutDir.walkTopDown().count { it.isFile && it.name.endsWith(".class") }
        return JavaCompileResult(
            success = true,
            classesDir = classesOutDir,
            sourceFileCount = javaFiles.size,
            compiledClassCount = compiledCount,
            diagnostics = diagnostics
        )
    }

    private fun hasNbJavacMain(): Boolean =
        listOf("com.sun.tools.javac.Main", "openjdk.tools.javac.Main").any { name ->
            runCatching { Class.forName(name) }.isSuccess
        }

    private fun invokeNbJavacReflectively(
        androidJar: File,
        classpath: List<String>,
        classesOutDir: File,
        argListFile: File
    ): Pair<Boolean, String> {
        val candidateMainClasses = listOf(
            "com.sun.tools.javac.Main",
            "openjdk.tools.javac.Main"
        )
        val mainCls = candidateMainClasses.firstNotNullOfOrNull { name ->
            runCatching { Class.forName(name) }.getOrNull()
        } ?: return false to ""

        val args = ArrayList<String>().apply {
            add("-encoding")
            add("UTF-8")
            add("-source")
            add("8")
            add("-target")
            add("8")
            add("-XDstringConcat=inline")
            add("-bootclasspath")
            add(androidJar.absolutePath)
            if (classpath.isNotEmpty()) {
                add("-classpath")
                add(classpath.joinToString(File.pathSeparator))
            }
            add("-d")
            add(classesOutDir.absolutePath)
            add("-proc:none")
            add("-nowarn")
            add("@${argListFile.absolutePath}")
        }.toTypedArray()

        val outSw = StringWriter()
        val pw = PrintWriter(outSw)
        return try {
            val compileMethod = mainCls.getMethod("compile", Array<String>::class.java, PrintWriter::class.java)
            val exitCode = (compileMethod.invoke(null, args, pw) as? Int) ?: 1
            pw.flush()
            (exitCode == 0) to outSw.toString().trim()
        } catch (t: Throwable) {
            val cause = t.cause ?: t
            false to "Javac Error (${cause.javaClass.simpleName}): ${cause.message ?: "Invocation failed"}"
        }
    }

    private fun invokeEcj(
        sourceLevel: String,
        androidJar: File,
        classpath: List<String>,
        classesOutDir: File,
        argListFile: File
    ): Pair<Boolean, String> {
        try {
            System.setProperty("jdt.compiler.useSingleThread", "true")
        } catch (_: Throwable) { }

        val args = ArrayList<String>().apply {
            add("-encoding")
            add("UTF-8")
            add("-source")
            add(sourceLevel)
            add("-target")
            add(sourceLevel)
            add("-bootclasspath")
            add(androidJar.absolutePath)
            if (classpath.isNotEmpty()) {
                add("-classpath")
                add(classpath.joinToString(File.pathSeparator))
            }
            add("-d")
            add(classesOutDir.absolutePath)
            add("-proc:none")
            add("-nowarn")
            add("-Xemacs")
            add("@${argListFile.absolutePath}")
        }

        val outSw = StringWriter()
        val errSw = StringWriter()
        return try {
            val success = BatchCompiler.compile(
                args.toTypedArray(),
                PrintWriter(outSw),
                PrintWriter(errSw),
                null
            )
            val combined = buildString {
                val e = errSw.toString().trim()
                val o = outSw.toString().trim()
                if (e.isNotEmpty()) appendLine(e)
                if (o.isNotEmpty()) appendLine(o)
            }.trim()
            success to combined
        } catch (t: Throwable) {
            false to "ECJ Invocation Error (${t.javaClass.simpleName}): ${t.message ?: ""}"
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

    private fun parseCompilerOutput(output: String, moduleDir: File): List<BuildDiagnosticItem> {
        val list = mutableListOf<BuildDiagnosticItem>()
        val prefix = moduleDir.absolutePath + "/"
        output.lines().forEach { line ->
            val m = DIAG_REGEX.find(line.trim()) ?: return@forEach
            val rawPath = m.groupValues[1]
            val ln = m.groupValues[2].toIntOrNull() ?: 0
            val kind = m.groupValues[3].lowercase()
            val msg = m.groupValues[4]
            list.add(
                BuildDiagnosticItem(
                    filePath = rawPath.removePrefix(prefix),
                    line = ln,
                    column = 0,
                    message = msg,
                    isError = kind == "error"
                )
            )
        }
        return list
    }
}