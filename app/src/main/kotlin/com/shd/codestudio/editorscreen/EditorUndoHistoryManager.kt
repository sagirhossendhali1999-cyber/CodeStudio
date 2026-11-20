package com.shd.codestudio.editorscreen

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal data class DiskUndoEntry(
    val file: File,
    val cursorOffset: Int,
    val versionId: Long,
    val createdAtMs: Long,
    var isComposing: Boolean = false
)

internal class EditorUndoHistoryManager(context: Context) {

    companion object {
        private const val HISTORY_DIR_NAME = "CodeStudioUndoHistory"
        private const val EXPIRY_MILLIS = 6L * 60 * 60 * 1000L // ৬ ঘণ্টা পর অটো ডিলিট
        private const val MAX_STACK_STEPS = 200
    }

    private val rootDir = File(context.cacheDir, HISTORY_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }
    private val sessionDir = File(rootDir, "session_${System.currentTimeMillis()}").apply {
        if (!exists()) mkdirs()
    }

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val seq = AtomicLong(0L)
    private val pendingInMemory = ConcurrentHashMap<String, List<String>>()

    val undoStack = ArrayDeque<DiskUndoEntry>()
    val redoStack = ArrayDeque<DiskUndoEntry>()

    init {
        cleanupExpiredSnapshots()
    }

    fun cleanupExpiredSnapshots() {
        ioExecutor.execute {
            try {
                val cutoff = System.currentTimeMillis() - EXPIRY_MILLIS
                if (!rootDir.exists()) return@execute
                val children = rootDir.listFiles() ?: return@execute
                for (dir in children) {
                    if (dir.isDirectory) {
                        val files = dir.listFiles()
                        if (files != null) {
                            for (f in files) {
                                if (f.lastModified() < cutoff) {
                                    f.delete()
                                }
                            }
                        }
                        if (dir != sessionDir && (dir.listFiles()?.isEmpty() == true) && dir.lastModified() < cutoff) {
                            dir.delete()
                        }
                    } else if (dir.lastModified() < cutoff) {
                        dir.delete()
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun resetForNewFile() {
        val toDelete = ArrayList<File>(undoStack.size + redoStack.size)
        for (e in undoStack) {
            pendingInMemory.remove(e.file.absolutePath)
            toDelete.add(e.file)
        }
        for (e in redoStack) {
            pendingInMemory.remove(e.file.absolutePath)
            toDelete.add(e.file)
        }
        undoStack.clear()
        redoStack.clear()

        if (toDelete.isNotEmpty()) {
            ioExecutor.execute {
                for (f in toDelete) {
                    try {
                        if (f.exists()) f.delete()
                    } catch (_: Exception) { }
                }
            }
        }
        cleanupExpiredSnapshots()
    }

    fun pushUndoSnapshot(
        linesSnapshot: List<String>,
        cursorOffset: Int,
        versionId: Long,
        isComposing: Boolean = false
    ) {
        if (!sessionDir.exists()) sessionDir.mkdirs()

        // Redo স্ট্যাক ক্লিয়ার এবং ডিস্ক ফাইল ডিলিট
        if (redoStack.isNotEmpty()) {
            val redoFiles = ArrayList<File>(redoStack.size)
            while (redoStack.isNotEmpty()) {
                val r = redoStack.removeLast()
                pendingInMemory.remove(r.file.absolutePath)
                redoFiles.add(r.file)
            }
            ioExecutor.execute {
                for (f in redoFiles) {
                    try {
                        if (f.exists()) f.delete()
                    } catch (_: Exception) { }
                }
            }
        }

        val now = System.currentTimeMillis()
        val id = seq.incrementAndGet()
        val snapFile = File(sessionDir, "snap_${id}_$now.gz")
        val entry = DiskUndoEntry(
            file = snapFile,
            cursorOffset = cursorOffset,
            versionId = versionId,
            createdAtMs = now,
            isComposing = isComposing
        )

        undoStack.addLast(entry)
        pendingInMemory[snapFile.absolutePath] = linesSnapshot

        // সর্বোচ্চ ২০০টি স্টেপ পর্যন্ত রাখা ও পুরোনোগুলো মুছে ফেলা
        val trimmedFiles = ArrayList<File>()
        while (undoStack.size > MAX_STACK_STEPS) {
            val old = undoStack.removeFirst()
            pendingInMemory.remove(old.file.absolutePath)
            trimmedFiles.add(old.file)
        }

        ioExecutor.execute {
            for (f in trimmedFiles) {
                try {
                    if (f.exists()) f.delete()
                } catch (_: Exception) { }
            }
            writeSnapshotToDisk(snapFile, linesSnapshot)
            pendingInMemory.remove(snapFile.absolutePath)
        }

        if (id % 40L == 0L) {
            cleanupExpiredSnapshots()
        }
    }

    fun pushToRedo(linesSnapshot: List<String>, cursorOffset: Int, versionId: Long) {
        if (!sessionDir.exists()) sessionDir.mkdirs()
        val now = System.currentTimeMillis()
        val id = seq.incrementAndGet()
        val snapFile = File(sessionDir, "redo_${id}_$now.gz")
        val entry = DiskUndoEntry(
            file = snapFile,
            cursorOffset = cursorOffset,
            versionId = versionId,
            createdAtMs = now,
            isComposing = false
        )
        redoStack.addLast(entry)
        pendingInMemory[snapFile.absolutePath] = linesSnapshot

        val trimmedFiles = ArrayList<File>()
        while (redoStack.size > MAX_STACK_STEPS) {
            val old = redoStack.removeFirst()
            pendingInMemory.remove(old.file.absolutePath)
            trimmedFiles.add(old.file)
        }

        ioExecutor.execute {
            for (f in trimmedFiles) {
                try {
                    if (f.exists()) f.delete()
                } catch (_: Exception) { }
            }
            writeSnapshotToDisk(snapFile, linesSnapshot)
            pendingInMemory.remove(snapFile.absolutePath)
        }
    }

    fun popUndoAndRead(): Pair<DiskUndoEntry, List<String>>? {
        while (undoStack.isNotEmpty()) {
            val entry = undoStack.removeLast()
            val lines = readSnapshot(entry)
            if (lines != null) {
                return entry to lines
            }
        }
        return null
    }

    fun popRedoAndRead(): Pair<DiskUndoEntry, List<String>>? {
        while (redoStack.isNotEmpty()) {
            val entry = redoStack.removeLast()
            val lines = readSnapshot(entry)
            if (lines != null) {
                return entry to lines
            }
        }
        return null
    }

    fun finishComposing() {
        if (undoStack.isNotEmpty()) {
            undoStack.last().isComposing = false
        }
    }

    private fun writeSnapshotToDisk(targetFile: File, linesSnapshot: List<String>) {
        val tmpFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        try {
            BufferedWriter(
                OutputStreamWriter(
                    GZIPOutputStream(BufferedOutputStream(FileOutputStream(tmpFile), 16384)),
                    Charsets.UTF_8
                )
            ).use { writer ->
                writer.write(linesSnapshot.size.toString())
                writer.newLine()
                for (i in linesSnapshot.indices) {
                    writer.write(linesSnapshot[i])
                    writer.newLine()
                }
            }
            if (targetFile.exists()) targetFile.delete()
            tmpFile.renameTo(targetFile)
        } catch (_: Exception) {
            try {
                if (tmpFile.exists()) tmpFile.delete()
            } catch (_: Exception) { }
        }
    }

    private fun readSnapshot(entry: DiskUndoEntry): List<String>? {
        val key = entry.file.absolutePath
        pendingInMemory[key]?.let { memLines ->
            pendingInMemory.remove(key)
            return memLines
        }
        if (!entry.file.exists()) return null
        return try {
            val result = ArrayList<String>()
            BufferedReader(
                InputStreamReader(
                    GZIPInputStream(BufferedInputStream(FileInputStream(entry.file), 16384)),
                    Charsets.UTF_8
                )
            ).use { reader ->
                val countLine = reader.readLine() ?: return null
                val count = countLine.toIntOrNull() ?: return null
                result.ensureCapacity(count.coerceAtMost(500_000))
                for (i in 0 until count) {
                    result.add(reader.readLine() ?: "")
                }
            }
            ioExecutor.execute {
                try {
                    if (entry.file.exists()) entry.file.delete()
                } catch (_: Exception) { }
            }
            result
        } catch (_: Exception) {
            null
        }
    }

    fun shutdown() {
        resetForNewFile()
        ioExecutor.shutdown()
    }
}