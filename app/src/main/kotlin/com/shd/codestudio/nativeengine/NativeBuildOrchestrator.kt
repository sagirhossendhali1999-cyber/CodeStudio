package com.shd.codestudio.nativeengine

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicReference

// ================= ১. আলাদা মেমোরি হিপের :compiler সার্ভিস প্রসেস =================
class CompilerWorkerService : Service() {

    companion object {
        const val MSG_START_BUILD = 1
        const val MSG_CANCEL_BUILD = 2
        const val MSG_LOG = 10
        const val MSG_COMPLETE = 11
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeBuildJob: Job? = null

    @Volatile
    private var cancelled = false

    private val incomingHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_START_BUILD -> {
                    val replyTo = msg.replyTo ?: return
                    val data = msg.data
                    val projectName = data.getString("projectName") ?: ""
                    val variantName = data.getString("variant") ?: EngineBuildVariant.DEBUG.name
                    val cleanFirst = data.getBoolean("cleanFirst", false)
                    val variant = runCatching { EngineBuildVariant.valueOf(variantName) }
                        .getOrDefault(EngineBuildVariant.DEBUG)

                    val ksPath = data.getString("ksPath")
                    val customKs = if (!ksPath.isNullOrBlank()) {
                        CustomKeystoreConfig(
                            keystoreFile = File(ksPath),
                            keystorePassword = data.getString("ksPass") ?: "",
                            keyAlias = data.getString("keyAlias") ?: "",
                            keyPassword = data.getString("keyPass") ?: ""
                        )
                    } else null

                    cancelled = false
                    activeBuildJob?.cancel()
                    activeBuildJob = serviceScope.launch {
                        val summary = NativeBuildOrchestrator.executeBuildDirect(
                            context = applicationContext,
                            request = NativeBuildRequest(
                                projectName = projectName,
                                variant = variant,
                                cleanFirst = cleanFirst,
                                customKeystore = customKs
                            ),
                            isCancelled = { cancelled },
                            onLog = { line ->
                                sendReply(replyTo, MSG_LOG, Bundle().apply { putString("line", line.take(1500)) })
                            }
                        )
                        sendReply(replyTo, MSG_COMPLETE, Bundle().apply {
                            putString("summaryJson", serializeSummary(summary))
                        })
                    }
                }

                MSG_CANCEL_BUILD -> {
                    cancelled = true
                    activeBuildJob?.cancel()
                }
            }
        }
    }

    private val serviceMessenger = Messenger(incomingHandler)

    override fun onBind(intent: Intent?): IBinder = serviceMessenger.binder

    override fun onDestroy() {
        super.onDestroy()
        cancelled = true
        serviceScope.cancel()
    }

    private fun sendReply(target: Messenger, what: Int, data: Bundle) {
        try {
            val m = Message.obtain(null, what)
            m.data = data
            target.send(m)
        } catch (_: Exception) { }
    }

    private fun serializeSummary(s: NativeBuildSummary): String {
        val diagArr = JSONArray()
        s.diagnostics.take(80).forEach { d ->
            diagArr.put(JSONObject().apply {
                put("filePath", d.filePath)
                put("line", d.line)
                put("column", d.column)
                put("message", d.message.take(400))
                put("isError", d.isError)
            })
        }
        return JSONObject().apply {
            put("success", s.success)
            put("durationMs", s.durationMs)
            put("outputApkPath", s.outputApkPath)
            put("outputAabPath", s.outputAabPath)
            put("message", s.message.take(1000))
            put("executedTasks", s.executedTasks)
            put("skippedUpToDateTasks", s.skippedUpToDateTasks)
            put("diagnostics", diagArr)
        }.toString()
    }
}

// ================= ২. সম্পূর্ণ Gradle-মুক্ত Zero-Setup DAG বিল্ড অর্কেস্ট্রেটর =================
object NativeBuildOrchestrator {

    private const val ENGINE_REVISION = "rev6"

    suspend fun runBuild(
        context: Context,
        request: NativeBuildRequest,
        useIsolatedProcess: Boolean = true,
        isCancelled: () -> Boolean = { false },
        onLog: (String) -> Unit
    ): NativeBuildSummary {
        if (!useIsolatedProcess) {
            return executeBuildDirect(context, request, isCancelled, onLog)
        }

        val remoteResult = runViaIsolatedCompilerService(context, request, isCancelled, onLog)
        return remoteResult ?: executeBuildDirect(context, request, isCancelled, onLog)
    }

    private suspend fun runViaIsolatedCompilerService(
        context: Context,
        request: NativeBuildRequest,
        isCancelled: () -> Boolean,
        onLog: (String) -> Unit
    ): NativeBuildSummary? = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<NativeBuildSummary?>()
        val remoteMessengerRef = AtomicReference<Messenger?>(null)

        val replyMessenger = Messenger(object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    CompilerWorkerService.MSG_LOG -> {
                        msg.data.getString("line")?.let { onLog(it) }
                    }
                    CompilerWorkerService.MSG_COMPLETE -> {
                        val jsonStr = msg.data.getString("summaryJson") ?: ""
                        deferred.complete(deserializeSummary(jsonStr))
                    }
                }
            }
        })

        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val remote = Messenger(service)
                remoteMessengerRef.set(remote)
                try {
                    val m = Message.obtain(null, CompilerWorkerService.MSG_START_BUILD).apply {
                        replyTo = replyMessenger
                        data = Bundle().apply {
                            putString("projectName", request.projectName)
                            putString("variant", request.variant.name)
                            putBoolean("cleanFirst", request.cleanFirst)
                            request.customKeystore?.let { ks ->
                                putString("ksPath", ks.keystoreFile.absolutePath)
                                putString("ksPass", ks.keystorePassword)
                                putString("keyAlias", ks.keyAlias)
                                putString("keyPass", ks.keyPassword)
                            }
                        }
                    }
                    remote.send(m)
                } catch (_: Exception) {
                    if (!deferred.isCompleted) deferred.complete(null)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (!deferred.isCompleted) deferred.complete(null)
            }
        }

        val bound = try {
            context.bindService(
                Intent(context, CompilerWorkerService::class.java),
                conn,
                Context.BIND_AUTO_CREATE
            )
        } catch (_: Exception) {
            false
        }

        if (!bound) return@withContext null

        val cancelMonitor = launch {
            while (!deferred.isCompleted) {
                if (isCancelled()) {
                    try {
                        remoteMessengerRef.get()?.send(Message.obtain(null, CompilerWorkerService.MSG_CANCEL_BUILD))
                    } catch (_: Exception) { }
                    break
                }
                delay(150)
            }
        }

        return@withContext try {
            deferred.await()
        } finally {
            cancelMonitor.cancel()
            try {
                context.unbindService(conn)
            } catch (_: Exception) { }
        }
    }

    suspend fun executeBuildDirect(
        context: Context,
        request: NativeBuildRequest,
        isCancelled: () -> Boolean = { false },
        onLog: (String) -> Unit
    ): NativeBuildSummary = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        onLog("CodeStudio Native Engine (${request.variant.label}) [Offline Auto-Pilot]")

        val projectModel = DeclarativeProjectParser.parseProject(context, request.projectName)
        val appMod = projectModel.appModule
        if (!projectModel.rootDir.exists()) {
            return@withContext NativeBuildSummary(
                success = false,
                durationMs = System.currentTimeMillis() - startMs,
                message = "Project directory not found."
            )
        }

        val extraModules = projectModel.modules.filter {
            it.moduleName != appMod.moduleName && it.moduleName in appMod.projectDependencies
        }
        val extraSourceDirs = extraModules.flatMap { it.sourceDirs }
        val allDirectDeps = (appMod.dependencies + extraModules.flatMap { it.dependencies })
            .distinctBy { it.groupArtifactKey }
        val allBoms = (appMod.bomCoordinates + extraModules.flatMap { it.bomCoordinates })
            .distinctBy { it.groupArtifactKey }

        // AAPT2 বাইনারি নিশ্চিতকরণ
        val aapt2Bin = Aapt2ResourcePipeline.resolveAapt2Binary(context)
        if (aapt2Bin == null) {
            val msg = "ARM64 AAPT2 binary missing. Please ensure libaapt2.so is located in app/src/main/jniLibs/arm64-v8a/."
            onLog("ERROR: $msg")
            return@withContext NativeBuildSummary(
                success = false,
                durationMs = System.currentTimeMillis() - startMs,
                message = msg
            )
        }

        // ================= Zero-Setup ১: বান্ডেল্ড বা অনলাইন Android SDK নিশ্চিতকরণ =================
        var androidJar = Aapt2ResourcePipeline.resolveAndroidJar(context, appMod.compileSdk)
        if (androidJar == null || !androidJar.exists()) {
            androidJar = SdkEngineManager.autoEnsureAndroidJar(
                context = context,
                targetApi = appMod.compileSdk,
                isCancelled = isCancelled
            ) { line ->
                onLog("  $line")
            }
        }

        if (isCancelled()) {
            return@withContext NativeBuildSummary(
                success = false,
                durationMs = System.currentTimeMillis() - startMs,
                message = "Build cancelled by user"
            )
        }

        if (androidJar == null || !androidJar.exists()) {
            val msg = "Could not setup Android SDK Platform. Please check your internet connection or Build Runtime settings."
            onLog("ERROR: $msg")
            return@withContext NativeBuildSummary(
                success = false,
                durationMs = System.currentTimeMillis() - startMs,
                message = msg
            )
        }

        // ================= Zero-Setup ২: Kotlin সোর্স থাকলে বান্ডেল্ড Kotlin+Compose Pack নিশ্চিতকরণ =================
        val needsKotlin = KotlinNativeCompiler.hasKotlinSources(appMod, extraSourceDirs)
        if (needsKotlin && !SdkEngineManager.isKotlincInstalled(context)) {
            val (ok, packMsg) = SdkEngineManager.autoEnsureKotlincPack(
                context = context,
                isCancelled = isCancelled
            ) { line ->
                onLog("  $line")
            }
            if (!ok) {
                onLog("ERROR: $packMsg")
                return@withContext NativeBuildSummary(
                    success = false,
                    durationMs = System.currentTimeMillis() - startMs,
                    message = packMsg
                )
            }
        }

        if (isCancelled()) {
            return@withContext NativeBuildSummary(
                success = false,
                durationMs = System.currentTimeMillis() - startMs,
                message = "Build cancelled by user"
            )
        }

        val engineModeStr = if (needsKotlin) {
            buildList {
                add("Kotlin")
                if (appMod.composeEnabled) add("Compose")
                if (appMod.serializationEnabled) add("Serialization")
                if (appMod.parcelizeEnabled) add("Parcelize")
                add("Java")
            }.joinToString("+")
        } else {
            "Java"
        }
        onLog("SDK: ${androidJar.parentFile?.name}/${androidJar.name} • App: ${appMod.applicationId} • Mode: $engineModeStr")

        val activeKeystore = if (!request.variant.isDebug) {
            request.customKeystore ?: SdkEngineManager.loadKeystoreConfig(context)
        } else {
            request.customKeystore
        }

        if (activeKeystore != null) {
            onLog("Signing: Custom Keystore (${activeKeystore.keystoreFile.name})")
        }

        val workDir = File(appMod.moduleDir, "build/codestudio_engine/${request.variant.label.lowercase()}")
        val subFolder = if (request.variant == EngineBuildVariant.RELEASE) "release" else "debug"
        val finalApkFile = File(appMod.moduleDir, "build/outputs/apk/$subFolder/app-$subFolder.apk")

        if (request.cleanFirst) {
            if (workDir.exists()) workDir.deleteRecursively()
            if (finalApkFile.exists()) finalApkFile.delete()
        }
        workDir.mkdirs()

        val dag = TaskDagEngine(File(workDir, ".cache/task_fingerprints.json"))
        if (request.cleanFirst) dag.clearCache()

        var resolvedDeps = MavenResolutionResult(emptyList(), emptyList())
        val mergedManifest = File(workDir, "intermediates/merged_manifest/AndroidManifest.xml")
        var aaptRes = Aapt2PipelineResult(false, File(workDir, "intermediates/res_linked/resources.ap_"), File(workDir, "generated/source/java"))
        val kotlinClassesDir = File(workDir, "intermediates/kotlinc_classes")
        val javaClassesDir = File(workDir, "intermediates/javac_classes")
        var dexRes = DexPipelineResult(false, emptyList())

        val allDiagnostics = ArrayList<BuildDiagnosticItem>()

        fun resolveExtraKotlinRuntimeJars(): List<File> =
            KotlinNativeCompiler.resolveBundledRuntimeJars(
                context = context,
                module = appMod,
                classpathJars = resolvedDeps.allJars,
                hasKotlin = needsKotlin
            )

        val tasks = listOf(
            TaskNode(
                id = "resolveDeps",
                displayName = ":app:resolveMavenDependencies",
                inputStrings = {
                    listOf(
                        projectModel.repositories.joinToString(","),
                        allDirectDeps.joinToString(",") { it.notation },
                        allBoms.joinToString(",") { it.notation }
                    )
                },
                forceRun = true
            ) { ctx ->
                resolvedDeps = MavenDependencyResolver.resolveAll(
                    context = context,
                    repositories = projectModel.repositories,
                    directDependencies = allDirectDeps,
                    bomCoordinates = allBoms,
                    bypassGraphCache = request.cleanFirst,
                    isCancelled = ctx.isCancelled,
                    onLog = ctx.log
                )
                resolvedDeps.warnings.forEach { ctx.log("  WARN: $it") }
            },

            TaskNode(
                id = "mergeManifest",
                displayName = ":app:process${request.variant.label}Manifest",
                dependencies = listOf("resolveDeps"),
                inputFiles = { listOfNotNull(appMod.manifestFile) + resolvedDeps.libraries.mapNotNull { it.manifestFile } },
                inputStrings = {
                    listOf(
                        ENGINE_REVISION,
                        appMod.applicationId,
                        appMod.namespace,
                        appMod.minSdk.toString(),
                        appMod.targetSdk.toString(),
                        appMod.versionCode.toString(),
                        appMod.versionName,
                        request.variant.isDebug.toString()
                    )
                },
                outputFiles = { listOf(mergedManifest) }
            ) { ctx ->
                val res = AndroidManifestMerger.merge(
                    module = appMod,
                    libraries = resolvedDeps.libraries,
                    outputManifestFile = mergedManifest,
                    injectDebugCrashBridge = request.variant.isDebug
                )
                res.warnings.forEach { ctx.log("  WARN: $it") }
            },

            TaskNode(
                id = "aapt2Resources",
                displayName = ":app:process${request.variant.label}Resources",
                dependencies = listOf("mergeManifest"),
                inputFiles = {
                    appMod.resDirs +
                        appMod.assetsDirs +
                        extraModules.flatMap { it.resDirs + it.assetsDirs } +
                        listOfNotNull(mergedManifest, appMod.googleServicesFile)
                },
                inputStrings = {
                    listOf(
                        ENGINE_REVISION,
                        appMod.applicationId,
                        appMod.namespace,
                        appMod.minSdk.toString(),
                        appMod.targetSdk.toString(),
                        appMod.versionCode.toString(),
                        appMod.versionName,
                        appMod.viewBindingEnabled.toString(),
                        appMod.buildConfigEnabled.toString(),
                        request.variant.name,
                        resolvedDeps.libraries.size.toString()
                    )
                },
                outputFiles = { listOf(File(workDir, "intermediates/res_linked/resources.ap_"), File(workDir, "generated/source/java")) }
            ) { ctx ->
                aaptRes = Aapt2ResourcePipeline.execute(
                    context = context,
                    module = appMod,
                    variant = request.variant,
                    libraries = resolvedDeps.libraries,
                    mergedManifestFile = mergedManifest,
                    workDir = workDir,
                    extraProjectModules = extraModules,
                    isCancelled = ctx.isCancelled,
                    onLog = ctx.log
                )
                allDiagnostics.addAll(aaptRes.diagnostics)
                if (!aaptRes.success) throw RuntimeException(aaptRes.errorMessage)
            },

            TaskNode(
                id = "compileKotlin",
                displayName = ":app:compile${request.variant.label}Kotlin",
                dependencies = listOf("aapt2Resources"),
                inputFiles = {
                    appMod.sourceDirs +
                        extraSourceDirs +
                        listOf(File(workDir, "generated/source/java")) +
                        resolvedDeps.allJars +
                        appMod.localJarFiles
                },
                inputStrings = {
                    listOf(
                        ENGINE_REVISION,
                        appMod.kotlinVersion,
                        appMod.javaVersion.toString(),
                        appMod.compileSdk.toString(),
                        appMod.composeEnabled.toString(),
                        appMod.serializationEnabled.toString(),
                        appMod.parcelizeEnabled.toString(),
                        request.variant.name
                    )
                },
                outputFiles = { listOf(kotlinClassesDir) }
            ) { ctx ->
                val ktRes = KotlinNativeCompiler.compile(
                    context = context,
                    module = appMod,
                    generatedJavaDir = File(workDir, "generated/source/java"),
                    classpathJars = resolvedDeps.allJars,
                    androidJar = androidJar,
                    classesOutDir = kotlinClassesDir,
                    workDir = workDir,
                    extraSourceDirs = extraSourceDirs,
                    onLog = ctx.log
                )
                allDiagnostics.addAll(ktRes.diagnostics)
                if (!ktRes.success) throw RuntimeException(ktRes.errorMessage)
                if (ktRes.kotlinFileCount > 0) {
                    ctx.log("  Compiled ${ktRes.kotlinFileCount} Kotlin file(s) -> ${ktRes.compiledClassCount} class(es)")
                }
            },

            TaskNode(
                id = "compileJava",
                displayName = ":app:compile${request.variant.label}Java",
                dependencies = listOf("compileKotlin"),
                inputFiles = {
                    appMod.sourceDirs +
                        extraSourceDirs +
                        listOf(File(workDir, "generated/source/java"), kotlinClassesDir) +
                        resolvedDeps.allJars +
                        appMod.localJarFiles
                },
                inputStrings = { listOf(ENGINE_REVISION, appMod.javaVersion.toString(), appMod.compileSdk.toString(), request.variant.name) },
                outputFiles = { listOf(javaClassesDir) }
            ) { ctx ->
                val jRes = JavaEcjCompiler.compile(
                    module = appMod,
                    generatedJavaDir = File(workDir, "generated/source/java"),
                    classpathJars = resolvedDeps.allJars + resolveExtraKotlinRuntimeJars() + listOf(kotlinClassesDir),
                    androidJar = androidJar,
                    classesOutDir = javaClassesDir,
                    workDir = workDir,
                    extraSourceDirs = extraSourceDirs,
                    onLog = ctx.log
                )
                allDiagnostics.addAll(jRes.diagnostics)
                if (!jRes.success) throw RuntimeException(jRes.errorMessage)
                if (jRes.sourceFileCount > 0) {
                    ctx.log("  Compiled ${jRes.sourceFileCount} Java file(s) -> ${jRes.compiledClassCount} class(es)")
                }
            },

            TaskNode(
                id = "dexBuilder",
                displayName = ":app:dexBuilder${request.variant.label}",
                dependencies = listOf("compileJava"),
                inputFiles = {
                    listOf(javaClassesDir, kotlinClassesDir) +
                        resolvedDeps.allJars +
                        resolveExtraKotlinRuntimeJars() +
                        appMod.localJarFiles +
                        appMod.proguardFiles
                },
                inputStrings = { listOf(ENGINE_REVISION, request.variant.name, appMod.minSdk.toString(), appMod.applicationId) },
                outputFiles = { listOf(File(workDir, "intermediates/dex_out")) }
            ) { ctx ->
                dexRes = D8DexPipeline.compileToDex(
                    context = context,
                    module = appMod,
                    variant = request.variant,
                    classesDirs = listOf(javaClassesDir, kotlinClassesDir),
                    libraryJars = resolvedDeps.allJars + resolveExtraKotlinRuntimeJars(),
                    libraries = resolvedDeps.libraries,
                    androidJar = androidJar,
                    workDir = workDir,
                    isCancelled = ctx.isCancelled,
                    onLog = ctx.log
                )
                allDiagnostics.addAll(dexRes.diagnostics)
                if (!dexRes.success) throw RuntimeException(dexRes.errorMessage)
            },

            TaskNode(
                id = "packageApk",
                displayName = ":app:package${request.variant.label}",
                dependencies = listOf("dexBuilder", "aapt2Resources"),
                inputFiles = {
                    listOf(
                        File(workDir, "intermediates/res_linked/resources.ap_"),
                        File(workDir, "intermediates/dex_out"),
                        File(appMod.moduleDir, "src/main/jniLibs")
                    ) + resolvedDeps.allJars + resolveExtraKotlinRuntimeJars() + appMod.localJarFiles + resolvedDeps.libraries.mapNotNull { it.jniDir }
                },
                inputStrings = {
                    listOf(
                        ENGINE_REVISION,
                        appMod.applicationId,
                        request.variant.name,
                        appMod.minSdk.toString(),
                        activeKeystore?.keystoreFile?.absolutePath ?: "debug"
                    )
                },
                outputFiles = { listOf(finalApkFile) }
            ) { ctx ->
                val dexList = File(workDir, "intermediates/dex_out").listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".dex") }
                    ?.sortedBy { it.name }
                    ?: emptyList()
                val pkgRes = ApkPackagerAndSigner.packageAndSign(
                    context = context,
                    module = appMod,
                    resourcesApFile = File(workDir, "intermediates/res_linked/resources.ap_"),
                    dexFiles = dexList,
                    libraries = resolvedDeps.libraries,
                    libraryJars = resolvedDeps.allJars + resolveExtraKotlinRuntimeJars(),
                    workDir = workDir,
                    outputApkFile = finalApkFile,
                    customKeystore = activeKeystore,
                    onLog = ctx.log
                )
                if (!pkgRes.success) throw RuntimeException(pkgRes.errorMessage)
            }
        )

        val stats = dag.execute(tasks, isCancelled = isCancelled, onLog = onLog)
        val totalMs = System.currentTimeMillis() - startMs

        return@withContext if (stats.success) {
            onLog("BUILD SUCCESSFUL in ${String.format("%.2fs", totalMs / 1000.0)} (${stats.executedCount} executed, ${stats.upToDateCount} up-to-date)")
            NativeBuildSummary(
                success = true,
                durationMs = totalMs,
                outputApkPath = finalApkFile.absolutePath,
                message = "BUILD SUCCESSFUL",
                executedTasks = stats.executedCount,
                skippedUpToDateTasks = stats.upToDateCount,
                diagnostics = allDiagnostics
            )
        } else {
            onLog("BUILD FAILED: ${stats.errorMessage}")
            NativeBuildSummary(
                success = false,
                durationMs = totalMs,
                message = stats.errorMessage,
                executedTasks = stats.executedCount,
                skippedUpToDateTasks = stats.upToDateCount,
                diagnostics = allDiagnostics
            )
        }
    }

    private fun deserializeSummary(jsonStr: String): NativeBuildSummary? {
        if (jsonStr.isBlank()) return null
        return try {
            val obj = JSONObject(jsonStr)
            val diags = mutableListOf<BuildDiagnosticItem>()
            val arr = obj.optJSONArray("diagnostics")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val d = arr.getJSONObject(i)
                    diags.add(
                        BuildDiagnosticItem(
                            filePath = d.optString("filePath"),
                            line = d.optInt("line"),
                            column = d.optInt("column"),
                            message = d.optString("message"),
                            isError = d.optBoolean("isError", true)
                        )
                    )
                }
            }
            NativeBuildSummary(
                success = obj.optBoolean("success", false),
                durationMs = obj.optLong("durationMs", 0L),
                outputApkPath = obj.optString("outputApkPath", ""),
                outputAabPath = obj.optString("outputAabPath", ""),
                message = obj.optString("message", ""),
                executedTasks = obj.optInt("executedTasks", 0),
                skippedUpToDateTasks = obj.optInt("skippedUpToDateTasks", 0),
                diagnostics = diags
            )
        } catch (_: Exception) {
            null
        }
    }
}