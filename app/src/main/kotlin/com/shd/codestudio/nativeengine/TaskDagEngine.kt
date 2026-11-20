package com.shd.codestudio.nativeengine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class DagRunStats(
    val success: Boolean,
    val executedCount: Int,
    val upToDateCount: Int,
    val durationMs: Long,
    val errorMessage: String = ""
)

class TaskDagEngine(
    private val cacheFile: File
) {
    private val fingerprintMap = ConcurrentHashMap<String, String>()

    init {
        loadFingerprints()
    }

    fun clearCache() {
        fingerprintMap.clear()
        try {
            if (cacheFile.exists()) cacheFile.delete()
        } catch (_: Exception) { }
    }

    suspend fun execute(
        tasks: List<TaskNode>,
        isCancelled: () -> Boolean = { false },
        onLog: (String) -> Unit
    ): DagRunStats = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        if (tasks.isEmpty()) {
            return@withContext DagRunStats(true, 0, 0, 0L)
        }

        val taskMap = LinkedHashMap<String, TaskNode>(tasks.size)
        for (t in tasks) {
            taskMap[t.id] = t
        }

        val completed = HashSet<String>()
        val executedCount = AtomicInteger(0)
        val upToDateCount = AtomicInteger(0)
        val execCtx = TaskExecutionContext(log = onLog, isCancelled = isCancelled)

        try {
            while (completed.size < taskMap.size) {
                if (isCancelled()) {
                    throw CancellationException("Build cancelled by user")
                }

                val readyWave = taskMap.values.filter { task ->
                    task.id !in completed && task.dependencies.all { dep -> dep in completed || dep !in taskMap }
                }

                if (readyWave.isEmpty()) {
                    val remaining = taskMap.keys.filter { it !in completed }
                    throw IllegalStateException("Cyclic or unresolved task dependency detected: ${remaining.joinToString()}")
                }

                coroutineScope {
                    readyWave.map { task ->
                        async(Dispatchers.IO) {
                            if (isCancelled()) {
                                throw CancellationException("Build cancelled by user")
                            }

                            val newFingerprint = computeTaskFingerprint(task)
                            val prevFingerprint = fingerprintMap[task.id]
                            val outputs = task.outputFiles()
                            val outputsReady = outputs.isNotEmpty() && outputs.all { f ->
                                f.exists() && (f.isDirectory || f.length() > 0L)
                            }

                            if (!task.forceRun && prevFingerprint != null && prevFingerprint == newFingerprint && outputsReady) {
                                upToDateCount.incrementAndGet()
                                onLog("> Task ${task.displayName} UP-TO-DATE")
                            } else {
                                val tStart = System.currentTimeMillis()
                                onLog("> Task ${task.displayName}")
                                task.action(execCtx)
                                val elapsed = System.currentTimeMillis() - tStart
                                executedCount.incrementAndGet()
                                val finalFingerprint = computeTaskFingerprint(task)
                                fingerprintMap[task.id] = finalFingerprint
                                onLog("  ✓ ${task.displayName} (${elapsed}ms)")
                            }
                        }
                    }.awaitAll()
                }

                readyWave.forEach { completed.add(it.id) }
            }

            saveFingerprints()
            DagRunStats(
                success = true,
                executedCount = executedCount.get(),
                upToDateCount = upToDateCount.get(),
                durationMs = System.currentTimeMillis() - startMs
            )
        } catch (ce: CancellationException) {
            saveFingerprints()
            DagRunStats(
                success = false,
                executedCount = executedCount.get(),
                upToDateCount = upToDateCount.get(),
                durationMs = System.currentTimeMillis() - startMs,
                errorMessage = ce.message ?: "Build cancelled"
            )
        } catch (t: Throwable) {
            saveFingerprints()
            val msg = t.message ?: "Task execution error"
            onLog("ERROR: $msg")
            DagRunStats(
                success = false,
                executedCount = executedCount.get(),
                upToDateCount = upToDateCount.get(),
                durationMs = System.currentTimeMillis() - startMs,
                errorMessage = msg
            )
        }
    }

    private fun computeTaskFingerprint(task: TaskNode): String {
        val digest = MessageDigest.getInstance("SHA-256")

        for (s in task.inputStrings()) {
            digest.update(s.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }

        val expandedFiles = expandFilesSorted(task.inputFiles())
        val buffer = ByteArray(16_384)
        for (file in expandedFiles) {
            digest.update(file.absolutePath.toByteArray(Charsets.UTF_8))
            digest.update(1.toByte())
            if (file.exists() && file.isFile) {
                val len = file.length()
                digest.update("$len:${file.lastModified()}".toByteArray(Charsets.UTF_8))
                if (len <= 1024L * 1024L) {
                    try {
                        FileInputStream(file).use { fis ->
                            var r: Int
                            while (fis.read(buffer).also { r = it } > 0) {
                                digest.update(buffer, 0, r)
                            }
                        }
                    } catch (_: Exception) { }
                }
            } else {
                digest.update("MISSING".toByteArray(Charsets.UTF_8))
            }
            digest.update(2.toByte())
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun expandFilesSorted(roots: List<File>): List<File> {
        val result = ArrayList<File>()
        for (root in roots) {
            if (!root.exists()) {
                result.add(root)
            } else if (root.isFile) {
                result.add(root)
            } else if (root.isDirectory) {
                root.walkTopDown()
                    .filter { it.isFile }
                    .forEach { result.add(it) }
            }
        }
        result.sortBy { it.absolutePath }
        return result
    }

    private fun loadFingerprints() {
        try {
            if (!cacheFile.exists()) return
            val json = JSONObject(cacheFile.readText(Charsets.UTF_8))
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                fingerprintMap[k] = json.optString(k, "")
            }
        } catch (_: Exception) { }
    }

    private fun saveFingerprints() {
        try {
            cacheFile.parentFile?.mkdirs()
            val json = JSONObject()
            fingerprintMap.forEach { (k, v) -> json.put(k, v) }
            cacheFile.writeText(json.toString(), Charsets.UTF_8)
        } catch (_: Exception) { }
    }
}