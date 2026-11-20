package com.shd.codestudio.editorscreen

import android.util.LruCache
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.max
import kotlin.math.min

data class DocPos(
    val line: Long,
    val col: Int
) : Comparable<DocPos> {
    override fun compareTo(other: DocPos): Int {
        val cmp = line.compareTo(other.line)
        return if (cmp != 0) cmp else col.compareTo(other.col)
    }
}

data class EditorSearchMatch(
    val line: Long,
    val startCol: Int,
    val endCol: Int
)

internal class HugeFileDocument {

    companion object {
        private const val HUGE_FILE_THRESHOLD_BYTES = 4L * 1024L * 1024L
        private const val INITIAL_SYNC_INDEX_BYTES = 2L * 1024L * 1024L
        private const val CHUNK_TARGET_BYTES = 256 * 1024
        private const val MAX_SINGLE_LINE_BYTES = 1024 * 1024
        private const val MAX_IN_MEMORY_CHUNK_LINES = 2500
        private const val MAX_UNDO_STEPS = 120
        private const val MAX_UNDO_DELTA_LINES = 10_000
        private const val MAX_UNDO_DELTA_CHARS = 2_000_000
    }

    private class DocChunk(
        val id: Long,
        var startLine: Long,
        var fileOffset: Long,
        var byteLength: Int,
        var lineCount: Int,
        var endsWithNewline: Boolean,
        var lines: ArrayList<String>? = null,
        var isDirty: Boolean = false
    )

    private data class UndoDelta(
        val startLine: Long,
        val oldLines: List<String>,
        val newLineCount: Int,
        val cursorBefore: DocPos,
        val versionBefore: Long,
        var isComposing: Boolean = false
    )

    private val lock = ReentrantReadWriteLock()
    private val chunkIdGen = AtomicLong(0L)
    private val versionGen = AtomicLong(0L)
    private val loadGeneration = AtomicLong(0L)

    private val chunks = ArrayList<DocChunk>()
    private val lruChunkCache = object : LruCache<Long, ArrayList<String>>(24) {}

    private val undoStack = ArrayDeque<UndoDelta>()
    private val redoStack = ArrayDeque<UndoDelta>()

    private var raf: RandomAccessFile? = null
    private var fileChannel: FileChannel? = null
    private var sourceFileRef: File? = null
    private var indexerExecutor: ExecutorService? = null

    @Volatile
    var lineCount: Long = 1L
        private set

    @Volatile
    var totalFileBytes: Long = 0L
        private set

    @Volatile
    var approxTotalChars: Long = 0L
        private set

    @Volatile
    var isHugeMode: Boolean = false
        private set

    @Volatile
    var isIndexing: Boolean = false
        private set

    private var currentVersionId: Long = 0L
    private var savedVersionId: Long = 0L

    var onIndexingProgress: ((lineCount: Long, bytesScanned: Long, totalBytes: Long, done: Boolean) -> Unit)? = null

    init {
        resetEmpty()
    }

    private fun resetEmpty() {
        lock.write {
            chunks.clear()
            lruChunkCache.evictAll()
            undoStack.clear()
            redoStack.clear()
            chunks.add(
                DocChunk(
                    id = chunkIdGen.incrementAndGet(),
                    startLine = 0L,
                    fileOffset = 0L,
                    byteLength = 0,
                    lineCount = 1,
                    endsWithNewline = false,
                    lines = arrayListOf(""),
                    isDirty = false
                )
            )
            lineCount = 1L
            totalFileBytes = 0L
            approxTotalChars = 0L
            isHugeMode = false
            isIndexing = false
            currentVersionId = 0L
            savedVersionId = 0L
            versionGen.set(0L)
        }
    }

    private fun closeResourcesOnly() {
        loadGeneration.incrementAndGet()
        isIndexing = false
        try {
            indexerExecutor?.shutdownNow()
        } catch (_: Exception) { }
        indexerExecutor = null
        try {
            fileChannel?.close()
        } catch (_: Exception) { }
        fileChannel = null
        try {
            raf?.close()
        } catch (_: Exception) { }
        raf = null
    }

    fun close() {
        closeResourcesOnly()
        lock.write {
            chunks.clear()
            lruChunkCache.evictAll()
            undoStack.clear()
            redoStack.clear()
        }
    }

    fun isDirty(): Boolean = currentVersionId != savedVersionId

    fun markSaved() {
        savedVersionId = currentVersionId
    }

    fun canUndo(): Boolean = lock.read { undoStack.isNotEmpty() }

    fun canRedo(): Boolean = lock.read { redoStack.isNotEmpty() }

    fun finishComposing() {
        lock.write {
            if (undoStack.isNotEmpty()) {
                undoStack.last().isComposing = false
            }
        }
    }

    fun loadFromLines(initialLines: List<String>, sourceFile: File? = null) {
        closeResourcesOnly()
        sourceFileRef = sourceFile
        val gen = loadGeneration.incrementAndGet()

        lock.write {
            chunks.clear()
            lruChunkCache.evictAll()
            undoStack.clear()
            redoStack.clear()

            val safeLines = if (initialLines.isEmpty()) listOf("") else initialLines
            var curStartLine = 0L
            var charAcc = 0L
            var idx = 0
            while (idx < safeLines.size) {
                val endIdx = min(idx + 1000, safeLines.size)
                val part = ArrayList<String>(endIdx - idx)
                for (i in idx until endIdx) {
                    val s = safeLines[i]
                    part.add(s)
                    charAcc += s.length + 1
                }
                chunks.add(
                    DocChunk(
                        id = chunkIdGen.incrementAndGet(),
                        startLine = curStartLine,
                        fileOffset = 0L,
                        byteLength = 0,
                        lineCount = part.size,
                        endsWithNewline = endIdx < safeLines.size,
                        lines = part,
                        isDirty = false
                    )
                )
                curStartLine += part.size
                idx = endIdx
            }

            lineCount = curStartLine.coerceAtLeast(1L)
            approxTotalChars = max(0L, charAcc - 1L)
            totalFileBytes = approxTotalChars
            isHugeMode = false
            isIndexing = false
            currentVersionId = 0L
            savedVersionId = 0L
            versionGen.set(0L)
        }

        if (loadGeneration.get() == gen) {
            onIndexingProgress?.invoke(lineCount, totalFileBytes, totalFileBytes, true)
        }
    }

    fun loadFromFile(file: File) {
        closeResourcesOnly()
        if (!file.exists() || !file.isFile) {
            loadFromLines(listOf("// File not found: ${file.name}"), null)
            return
        }

        val fileLen = file.length()
        if (fileLen <= HUGE_FILE_THRESHOLD_BYTES) {
            val loaded = ArrayList<String>()
            try {
                BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8), 32768).use { reader ->
                    var ln = reader.readLine()
                    while (ln != null) {
                        loaded.add(ln)
                        ln = reader.readLine()
                    }
                }
            } catch (e: Exception) {
                loaded.clear()
                loaded.add("// Error reading file: ${e.message}")
            }
            if (loaded.isEmpty()) loaded.add("")
            loadFromLines(loaded, file)
            return
        }

        val gen = loadGeneration.incrementAndGet()
        sourceFileRef = file
        val randomAccess = RandomAccessFile(file, "r")
        val channel = randomAccess.channel
        raf = randomAccess
        fileChannel = channel

        lock.write {
            chunks.clear()
            lruChunkCache.evictAll()
            undoStack.clear()
            redoStack.clear()
            lineCount = 0L
            totalFileBytes = fileLen
            approxTotalChars = fileLen
            isHugeMode = true
            isIndexing = true
            currentVersionId = 0L
            savedVersionId = 0L
            versionGen.set(0L)
        }

        val initialLimit = min(fileLen, INITIAL_SYNC_INDEX_BYTES)
        val firstOffset = indexRangeIntoChunks(channel, 0L, initialLimit, fileLen, gen)

        if (firstOffset >= fileLen) {
            finalizeTrailingNewlineIfNeeded(channel, fileLen)
            isIndexing = false
            onIndexingProgress?.invoke(lineCount, fileLen, fileLen, true)
            return
        }

        onIndexingProgress?.invoke(lineCount, firstOffset, fileLen, false)

        val executor = Executors.newSingleThreadExecutor()
        indexerExecutor = executor
        executor.execute {
            try {
                var curOffset = firstOffset
                var lastNotifyMs = System.currentTimeMillis()
                while (curOffset < fileLen && loadGeneration.get() == gen) {
                    val nextTarget = min(fileLen, curOffset + CHUNK_TARGET_BYTES * 8L)
                    val nextOffset = indexRangeIntoChunks(channel, curOffset, nextTarget, fileLen, gen)
                    if (nextOffset <= curOffset) break
                    curOffset = nextOffset

                    val now = System.currentTimeMillis()
                    if (now - lastNotifyMs >= 250L) {
                        lastNotifyMs = now
                        onIndexingProgress?.invoke(lineCount, curOffset, fileLen, false)
                    }
                }

                if (loadGeneration.get() == gen) {
                    finalizeTrailingNewlineIfNeeded(channel, fileLen)
                    isIndexing = false
                    onIndexingProgress?.invoke(lineCount, fileLen, fileLen, true)
                }
            } catch (_: Exception) {
                if (loadGeneration.get() == gen) {
                    isIndexing = false
                    onIndexingProgress?.invoke(lineCount, fileLen, fileLen, true)
                }
            }
        }
    }

    private fun indexRangeIntoChunks(
        channel: FileChannel,
        startPos: Long,
        endLimit: Long,
        totalFileLen: Long,
        gen: Long
    ): Long {
        var offset = startPos
        val readBuf = ByteArray(CHUNK_TARGET_BYTES)
        val newChunks = ArrayList<DocChunk>()

        while (offset < endLimit && loadGeneration.get() == gen) {
            val remaining = totalFileLen - offset
            if (remaining <= 0L) break

            val toRead = min(readBuf.size.toLong(), remaining).toInt()
            val bb = ByteBuffer.wrap(readBuf, 0, toRead)
            var totalRead = 0
            while (totalRead < toRead) {
                val r = channel.read(bb, offset + totalRead)
                if (r <= 0) break
                totalRead += r
            }
            if (totalRead <= 0) break

            var lastNl = -1
            for (i in totalRead - 1 downTo 0) {
                if (readBuf[i] == '\n'.code.toByte()) {
                    lastNl = i
                    break
                }
            }

            val chunkLen: Int
            val endsWithNl: Boolean
            var nlCount = 0

            if (lastNl != -1) {
                chunkLen = lastNl + 1
                endsWithNl = true
                for (i in 0 until chunkLen) {
                    if (readBuf[i] == '\n'.code.toByte()) nlCount++
                }
            } else if (offset + totalRead >= totalFileLen) {
                chunkLen = totalRead
                endsWithNl = false
            } else {
                // বড় সিঙ্গেল লাইন ১ MB পর্যন্ত খোঁজা
                val extraEnd = scanForwardForNewline(channel, offset + totalRead, min(totalFileLen, offset + MAX_SINGLE_LINE_BYTES))
                if (extraEnd > offset) {
                    chunkLen = (extraEnd - offset).toInt()
                    endsWithNl = true
                    nlCount = 1
                } else {
                    chunkLen = totalRead
                    endsWithNl = false
                }
            }

            val linesInChunk = if (endsWithNl) nlCount.coerceAtLeast(1) else (nlCount + 1)
            newChunks.add(
                DocChunk(
                    id = chunkIdGen.incrementAndGet(),
                    startLine = 0L,
                    fileOffset = offset,
                    byteLength = chunkLen,
                    lineCount = linesInChunk,
                    endsWithNewline = endsWithNl,
                    lines = null,
                    isDirty = false
                )
            )
            offset += chunkLen

            if (newChunks.size >= 16) {
                appendIndexedChunks(newChunks)
                newChunks.clear()
            }
        }

        if (newChunks.isNotEmpty() && loadGeneration.get() == gen) {
            appendIndexedChunks(newChunks)
        }
        return offset
    }

    private fun scanForwardForNewline(channel: FileChannel, startPos: Long, maxPos: Long): Long {
        val buf = ByteArray(32768)
        var pos = startPos
        while (pos < maxPos) {
            val toRead = min(buf.size.toLong(), maxPos - pos).toInt()
            val bb = ByteBuffer.wrap(buf, 0, toRead)
            val r = channel.read(bb, pos)
            if (r <= 0) break
            for (i in 0 until r) {
                if (buf[i] == '\n'.code.toByte()) {
                    return pos + i + 1L
                }
            }
            pos += r
        }
        return -1L
    }

    private fun appendIndexedChunks(newChunks: List<DocChunk>) {
        if (newChunks.isEmpty()) return
        lock.write {
            var curStart = if (chunks.isEmpty()) 0L else (chunks.last().startLine + chunks.last().lineCount)
            for (c in newChunks) {
                c.startLine = curStart
                chunks.add(c)
                curStart += c.lineCount
            }
            lineCount = curStart.coerceAtLeast(1L)
        }
    }

    private fun finalizeTrailingNewlineIfNeeded(channel: FileChannel, fileLen: Long) {
        if (fileLen == 0L) {
            lock.write {
                if (chunks.isEmpty()) {
                    chunks.add(
                        DocChunk(
                            id = chunkIdGen.incrementAndGet(),
                            startLine = 0L,
                            fileOffset = 0L,
                            byteLength = 0,
                            lineCount = 1,
                            endsWithNewline = false,
                            lines = arrayListOf(""),
                            isDirty = false
                        )
                    )
                    lineCount = 1L
                }
            }
            return
        }
        val lastByte = ByteArray(1)
        val bb = ByteBuffer.wrap(lastByte)
        val r = channel.read(bb, fileLen - 1L)
        if (r == 1 && lastByte[0] == '\n'.code.toByte()) {
            lock.write {
                val curStart = if (chunks.isEmpty()) 0L else (chunks.last().startLine + chunks.last().lineCount)
                chunks.add(
                    DocChunk(
                        id = chunkIdGen.incrementAndGet(),
                        startLine = curStart,
                        fileOffset = fileLen,
                        byteLength = 0,
                        lineCount = 1,
                        endsWithNewline = false,
                        lines = arrayListOf(""),
                        isDirty = false
                    )
                )
                lineCount = curStart + 1L
            }
        }
    }

    private fun findChunkIndexLocked(lineIndex: Long): Int {
        if (chunks.isEmpty()) return -1
        var low = 0
        var high = chunks.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val c = chunks[mid]
            if (lineIndex < c.startLine) {
                high = mid - 1
            } else if (lineIndex >= c.startLine + c.lineCount) {
                low = mid + 1
            } else {
                return mid
            }
        }
        return chunks.lastIndex.coerceAtLeast(0)
    }

    private fun getChunkLinesLocked(chunk: DocChunk): ArrayList<String> {
        chunk.lines?.let { return it }
        lruChunkCache.get(chunk.id)?.let { return it }

        val ch = fileChannel
        if (ch == null || chunk.byteLength <= 0) {
            val empty = arrayListOf("")
            lruChunkCache.put(chunk.id, empty)
            return empty
        }

        val bytes = ByteArray(chunk.byteLength)
        val bb = ByteBuffer.wrap(bytes)
        var readTotal = 0
        while (readTotal < chunk.byteLength) {
            val r = ch.read(bb, chunk.fileOffset + readTotal)
            if (r <= 0) break
            readTotal += r
        }

        val parsed = ArrayList<String>(chunk.lineCount.coerceAtLeast(1))
        var start = 0
        for (i in 0 until readTotal) {
            if (bytes[i] == '\n'.code.toByte()) {
                val end = if (i > start && bytes[i - 1] == '\r'.code.toByte()) i - 1 else i
                parsed.add(sanitizeNulls(String(bytes, start, end - start, Charsets.UTF_8)))
                start = i + 1
            }
        }
        if (start < readTotal) {
            val end = if (readTotal > start && bytes[readTotal - 1] == '\r'.code.toByte()) readTotal - 1 else readTotal
            parsed.add(sanitizeNulls(String(bytes, start, end - start, Charsets.UTF_8)))
        }
        if (parsed.isEmpty()) parsed.add("")

        // যদি লাইন গণনা সামান্য ভিন্ন হয়, অ্যাডজাস্ট
        while (parsed.size < chunk.lineCount) parsed.add("")
        lruChunkCache.put(chunk.id, parsed)
        return parsed
    }

    private fun sanitizeNulls(s: String): String {
        return if (s.indexOf('\u0000') != -1) s.replace('\u0000', ' ') else s
    }

    private fun ensureChunkMaterializedLocked(chunk: DocChunk): ArrayList<String> {
        chunk.lines?.let { return it }
        val loaded = ArrayList(getChunkLinesLocked(chunk))
        chunk.lines = loaded
        lruChunkCache.remove(chunk.id)
        return loaded
    }

    fun getLine(lineIndex: Long): String {
        return lock.read {
            if (chunks.isEmpty()) return@read ""
            val safeLine = lineIndex.coerceIn(0L, (lineCount - 1L).coerceAtLeast(0L))
            val cIdx = findChunkIndexLocked(safeLine)
            if (cIdx < 0) return@read ""
            val chunk = chunks[cIdx]
            val cLines = getChunkLinesLocked(chunk)
            val local = (safeLine - chunk.startLine).toInt().coerceIn(0, cLines.lastIndex)
            cLines[local]
        }
    }

    fun getLineLength(lineIndex: Long): Int = getLine(lineIndex).length

    fun getLinesWindow(startLine: Long, endLineInclusive: Long): Pair<List<String>, Int> {
        return lock.read {
            val maxL = (lineCount - 1L).coerceAtLeast(0L)
            val s = startLine.coerceIn(0L, maxL)
            val e = endLineInclusive.coerceIn(s, maxL)
            val result = ArrayList<String>((e - s + 1L).toInt().coerceAtMost(1000))
            var cur = s
            while (cur <= e) {
                val cIdx = findChunkIndexLocked(cur)
                if (cIdx < 0) break
                val chunk = chunks[cIdx]
                val cLines = getChunkLinesLocked(chunk)
                val localStart = (cur - chunk.startLine).toInt().coerceAtLeast(0)
                val localEnd = min(cLines.lastIndex, (e - chunk.startLine).toInt())
                for (i in localStart..localEnd) {
                    result.add(cLines[i])
                }
                cur = chunk.startLine + localEnd + 1L
            }
            result to s.toInt()
        }
    }

    fun getSubstring(start: DocPos, end: DocPos, maxChars: Int = 500_000): String {
        val (s, e) = if (start <= end) start to end else end to start
        if (s == e) return ""

        return lock.read {
            val sb = StringBuilder()
            val maxL = (lineCount - 1L).coerceAtLeast(0L)
            val sLine = s.line.coerceIn(0L, maxL)
            val eLine = e.line.coerceIn(sLine, maxL)

            if (sLine == eLine) {
                val lineStr = getLine(sLine)
                val c1 = s.col.coerceIn(0, lineStr.length)
                val c2 = e.col.coerceIn(c1, lineStr.length)
                return@read lineStr.substring(c1, min(c2, c1 + maxChars))
            }

            var cur = sLine
            while (cur <= eLine && sb.length < maxChars) {
                val cIdx = findChunkIndexLocked(cur)
                if (cIdx < 0) break
                val chunk = chunks[cIdx]
                val cLines = getChunkLinesLocked(chunk)
                val localStart = (cur - chunk.startLine).toInt().coerceAtLeast(0)
                val localEnd = min(cLines.lastIndex, (eLine - chunk.startLine).toInt())
                for (i in localStart..localEnd) {
                    val globalLine = chunk.startLine + i
                    val lineStr = cLines[i]
                    when (globalLine) {
                        sLine -> {
                            val c1 = s.col.coerceIn(0, lineStr.length)
                            sb.append(lineStr, c1, lineStr.length)
                        }
                        eLine -> {
                            sb.append('\n')
                            val c2 = e.col.coerceIn(0, lineStr.length)
                            sb.append(lineStr, 0, c2)
                        }
                        else -> {
                            sb.append('\n').append(lineStr)
                        }
                    }
                    if (sb.length >= maxChars) break
                }
                cur = chunk.startLine + localEnd + 1L
            }
            if (sb.length > maxChars) sb.setLength(maxChars)
            sb.toString()
        }
    }

    fun getFullText(maxChars: Int = 2_000_000): String {
        val lastLine = (lineCount - 1L).coerceAtLeast(0L)
        return getSubstring(DocPos(0L, 0), DocPos(lastLine, getLineLength(lastLine)), maxChars)
    }

    fun getTextBefore(pos: DocPos, n: Int): String {
        if (n <= 0) return ""
        return lock.read {
            var remaining = n
            var curLine = pos.line.coerceIn(0L, (lineCount - 1L).coerceAtLeast(0L))
            var curCol = pos.col.coerceIn(0, getLineLength(curLine))
            val parts = ArrayList<String>()

            while (remaining > 0 && curLine >= 0L) {
                val lineStr = getLine(curLine)
                val take = min(remaining, curCol)
                if (take > 0) {
                    parts.add(lineStr.substring(curCol - take, curCol))
                    remaining -= take
                }
                if (remaining > 0 && curLine > 0L) {
                    parts.add("\n")
                    remaining--
                    curLine--
                    curCol = getLineLength(curLine)
                } else {
                    break
                }
            }

            val sb = StringBuilder()
            for (i in parts.lastIndex downTo 0) {
                sb.append(parts[i])
            }
            sb.toString()
        }
    }

    fun getTextAfter(pos: DocPos, n: Int): String {
        if (n <= 0) return ""
        return lock.read {
            val maxL = (lineCount - 1L).coerceAtLeast(0L)
            var remaining = n
            var curLine = pos.line.coerceIn(0L, maxL)
            var curCol = pos.col.coerceIn(0, getLineLength(curLine))
            val sb = StringBuilder()

            while (remaining > 0 && curLine <= maxL) {
                val lineStr = getLine(curLine)
                val avail = (lineStr.length - curCol).coerceAtLeast(0)
                val take = min(remaining, avail)
                if (take > 0) {
                    sb.append(lineStr, curCol, curCol + take)
                    remaining -= take
                }
                if (remaining > 0 && curLine < maxL) {
                    sb.append('\n')
                    remaining--
                    curLine++
                    curCol = 0
                } else {
                    break
                }
            }
            sb.toString()
        }
    }

    fun replaceRange(
        start: DocPos,
        end: DocPos,
        replacement: String,
        cursorBefore: DocPos,
        isComposing: Boolean = false
    ): DocPos {
        val (s, e) = if (start <= end) start to end else end to start
        val sanitized = if (replacement.contains('\r')) {
            replacement.replace("\r\n", "\n").replace("\r", "\n")
        } else {
            replacement
        }
        val insertedLines = splitLinesPreserveEmpty(sanitized)

        return lock.write {
            val maxL = (lineCount - 1L).coerceAtLeast(0L)
            val sLine = s.line.coerceIn(0L, maxL)
            val eLine = e.line.coerceIn(sLine, maxL)

            val firstLineStr = getLine(sLine)
            val lastLineStr = getLine(eLine)
            val prefix = firstLineStr.substring(0, s.col.coerceIn(0, firstLineStr.length))
            val suffix = lastLineStr.substring(e.col.coerceIn(0, lastLineStr.length))

            val replacementLines = ArrayList<String>(insertedLines.size)
            if (insertedLines.size == 1) {
                replacementLines.add(prefix + insertedLines[0] + suffix)
            } else {
                replacementLines.add(prefix + insertedLines[0])
                for (i in 1 until insertedLines.lastIndex) {
                    replacementLines.add(insertedLines[i])
                }
                replacementLines.add(insertedLines.last() + suffix)
            }

            // Undo ডেল্টা ক্যাপচার (নিরাপদ সাইজের মধ্যে)
            val spanLines = (eLine - sLine + 1L)
            if (spanLines <= MAX_UNDO_DELTA_LINES) {
                val oldLinesCopy = ArrayList<String>(spanLines.toInt())
                var totalOldChars = 0
                for (l in sLine..eLine) {
                    val lineTxt = getLine(l)
                    totalOldChars += lineTxt.length
                    if (totalOldChars > MAX_UNDO_DELTA_CHARS) break
                    oldLinesCopy.add(lineTxt)
                }
                if (totalOldChars <= MAX_UNDO_DELTA_CHARS) {
                    val shouldMerge = isComposing &&
                        undoStack.isNotEmpty() &&
                        undoStack.last().isComposing &&
                        undoStack.last().startLine == sLine &&
                        undoStack.last().newLineCount == 1 &&
                        replacementLines.size == 1

                    if (shouldMerge) {
                        // প্রথম কম্পোজিং স্টেট ধরে রাখা
                    } else {
                        redoStack.clear()
                        undoStack.addLast(
                            UndoDelta(
                                startLine = sLine,
                                oldLines = oldLinesCopy,
                                newLineCount = replacementLines.size,
                                cursorBefore = cursorBefore,
                                versionBefore = currentVersionId,
                                isComposing = isComposing
                            )
                        )
                        while (undoStack.size > MAX_UNDO_STEPS) {
                            undoStack.removeFirst()
                        }
                    }
                } else {
                    undoStack.clear()
                    redoStack.clear()
                }
            } else {
                undoStack.clear()
                redoStack.clear()
            }

            applyLineSpliceLocked(sLine, eLine, replacementLines)
            currentVersionId = versionGen.incrementAndGet()

            if (insertedLines.size == 1) {
                DocPos(sLine, prefix.length + insertedLines[0].length)
            } else {
                DocPos(sLine + insertedLines.size - 1L, insertedLines.last().length)
            }
        }
    }

    private fun applyLineSpliceLocked(sLine: Long, eLine: Long, replacementLines: List<String>) {
        val startChunkIdx = findChunkIndexLocked(sLine)
        val endChunkIdx = findChunkIndexLocked(eLine)
        if (startChunkIdx < 0 || endChunkIdx < startChunkIdx) return

        val startChunk = chunks[startChunkIdx]
        val endChunk = chunks[endChunkIdx]
        val startLocal = (sLine - startChunk.startLine).toInt().coerceIn(0, startChunk.lineCount)
        val endLocal = (eLine - endChunk.startLine).toInt().coerceIn(0, (endChunk.lineCount - 1).coerceAtLeast(0))

        if (startChunkIdx == endChunkIdx) {
            val list = ensureChunkMaterializedLocked(startChunk)
            val safeEnd = (endLocal + 1).coerceAtMost(list.size)
            val safeStart = startLocal.coerceIn(0, safeEnd)
            list.subList(safeStart, safeEnd).clear()
            list.addAll(safeStart, replacementLines)
            startChunk.lineCount = list.size
            startChunk.isDirty = true

            if (list.size > MAX_IN_MEMORY_CHUNK_LINES) {
                splitMaterializedChunkLocked(startChunkIdx)
            }
            recomputeChunkStartLinesLocked(startChunkIdx)
        } else {
            val firstList = ensureChunkMaterializedLocked(startChunk)
            val lastList = ensureChunkMaterializedLocked(endChunk)

            val combined = ArrayList<String>(startLocal + replacementLines.size + (lastList.size - endLocal - 1).coerceAtLeast(0))
            for (i in 0 until startLocal.coerceAtMost(firstList.size)) {
                combined.add(firstList[i])
            }
            combined.addAll(replacementLines)
            for (i in (endLocal + 1) until lastList.size) {
                combined.add(lastList[i])
            }

            // মাঝের এবং শেষ চাঙ্ক রিমুভ
            for (i in endChunkIdx downTo (startChunkIdx + 1)) {
                val removed = chunks.removeAt(i)
                lruChunkCache.remove(removed.id)
            }

            startChunk.lines = combined
            startChunk.lineCount = combined.size
            startChunk.isDirty = true
            startChunk.endsWithNewline = endChunk.endsWithNewline

            if (combined.size > MAX_IN_MEMORY_CHUNK_LINES) {
                splitMaterializedChunkLocked(startChunkIdx)
            }
            recomputeChunkStartLinesLocked(startChunkIdx)
        }
    }

    private fun splitMaterializedChunkLocked(chunkIdx: Int) {
        val chunk = chunks[chunkIdx]
        val list = chunk.lines ?: return
        if (list.size <= MAX_IN_MEMORY_CHUNK_LINES) return

        val splitSize = 1000
        val newChunks = ArrayList<DocChunk>()
        var idx = 0
        while (idx < list.size) {
            val end = min(idx + splitSize, list.size)
            val slice = ArrayList(list.subList(idx, end))
            newChunks.add(
                DocChunk(
                    id = if (idx == 0) chunk.id else chunkIdGen.incrementAndGet(),
                    startLine = 0L,
                    fileOffset = 0L,
                    byteLength = 0,
                    lineCount = slice.size,
                    endsWithNewline = if (end < list.size) true else chunk.endsWithNewline,
                    lines = slice,
                    isDirty = true
                )
            )
            idx = end
        }
        chunks.removeAt(chunkIdx)
        chunks.addAll(chunkIdx, newChunks)
    }

    private fun recomputeChunkStartLinesLocked(fromIndex: Int) {
        if (chunks.isEmpty()) {
            chunks.add(
                DocChunk(
                    id = chunkIdGen.incrementAndGet(),
                    startLine = 0L,
                    fileOffset = 0L,
                    byteLength = 0,
                    lineCount = 1,
                    endsWithNewline = false,
                    lines = arrayListOf(""),
                    isDirty = true
                )
            )
            lineCount = 1L
            return
        }

        val startIdx = fromIndex.coerceIn(0, chunks.lastIndex)
        var curLine = if (startIdx == 0) 0L else (chunks[startIdx - 1].startLine + chunks[startIdx - 1].lineCount)
        for (i in startIdx until chunks.size) {
            val c = chunks[i]
            c.startLine = curLine
            curLine += c.lineCount
        }
        lineCount = curLine.coerceAtLeast(1L)
    }

    fun undo(): DocPos? {
        return lock.write {
            if (undoStack.isEmpty()) return@write null
            val delta = undoStack.removeLast()
            val curEndLine = (delta.startLine + delta.newLineCount - 1L).coerceAtMost((lineCount - 1L).coerceAtLeast(0L))
            val currentRangeLines = ArrayList<String>(delta.newLineCount.coerceAtLeast(1))
            for (l in delta.startLine..curEndLine) {
                currentRangeLines.add(getLine(l))
            }

            redoStack.addLast(
                UndoDelta(
                    startLine = delta.startLine,
                    oldLines = currentRangeLines,
                    newLineCount = delta.oldLines.size,
                    cursorBefore = delta.cursorBefore,
                    versionBefore = currentVersionId,
                    isComposing = false
                )
            )

            applyLineSpliceLocked(delta.startLine, curEndLine, delta.oldLines)
            currentVersionId = delta.versionBefore
            delta.cursorBefore
        }
    }

    fun redo(): DocPos? {
        return lock.write {
            if (redoStack.isEmpty()) return@write null
            val delta = redoStack.removeLast()
            val curEndLine = (delta.startLine + delta.newLineCount - 1L).coerceAtMost((lineCount - 1L).coerceAtLeast(0L))
            val currentRangeLines = ArrayList<String>(delta.newLineCount.coerceAtLeast(1))
            for (l in delta.startLine..curEndLine) {
                currentRangeLines.add(getLine(l))
            }

            undoStack.addLast(
                UndoDelta(
                    startLine = delta.startLine,
                    oldLines = currentRangeLines,
                    newLineCount = delta.oldLines.size,
                    cursorBefore = delta.cursorBefore,
                    versionBefore = currentVersionId,
                    isComposing = false
                )
            )

            applyLineSpliceLocked(delta.startLine, curEndLine, delta.oldLines)
            currentVersionId = delta.versionBefore
            val targetLine = (delta.startLine + delta.oldLines.size - 1L).coerceIn(0L, (lineCount - 1L).coerceAtLeast(0L))
            DocPos(targetLine, getLineLength(targetLine))
        }
    }

    fun getAllLinesIfSmall(maxLines: Int = 200_000): List<String>? {
        return lock.read {
            if (isHugeMode || lineCount > maxLines) return@read null
            val out = ArrayList<String>(lineCount.toInt())
            for (c in chunks) {
                out.addAll(getChunkLinesLocked(c))
            }
            out
        }
    }

    fun streamSearch(
        regex: Regex,
        maxMatches: Int,
        isCancelled: () -> Boolean
    ): List<EditorSearchMatch> {
        val matches = ArrayList<EditorSearchMatch>()
        var chunkIdx = 0
        while (!isCancelled() && matches.size < maxMatches) {
            val chunkSnapshot: Pair<Long, List<String>>? = lock.read {
                if (chunkIdx >= chunks.size) null
                else {
                    val c = chunks[chunkIdx]
                    c.startLine to ArrayList(getChunkLinesLocked(c))
                }
            }
            if (chunkSnapshot == null) break
            val (baseLine, cLines) = chunkSnapshot
            for (i in cLines.indices) {
                if (isCancelled() || matches.size >= maxMatches) break
                val lineStr = cLines[i]
                for (m in regex.findAll(lineStr)) {
                    if (m.value.isEmpty()) continue
                    matches.add(EditorSearchMatch(baseLine + i, m.range.first, m.range.last + 1))
                    if (matches.size >= maxMatches) break
                }
            }
            chunkIdx++
        }
        return matches
    }

    fun replaceAll(regex: Regex, replacement: String, useRegex: Boolean, cursorBefore: DocPos): Int {
        var totalReplaced = 0
        lock.write {
            undoStack.clear()
            redoStack.clear()
            var firstModifiedIdx = -1
            for (idx in chunks.indices) {
                val c = chunks[idx]
                val origLines = getChunkLinesLocked(c)
                var chunkHasMatch = false
                for (lineStr in origLines) {
                    if (regex.containsMatchIn(lineStr)) {
                        chunkHasMatch = true
                        break
                    }
                }
                if (!chunkHasMatch) continue

                if (firstModifiedIdx == -1) firstModifiedIdx = idx
                val materialized = ensureChunkMaterializedLocked(c)
                for (i in materialized.indices) {
                    val lineStr = materialized[i]
                    val hits = regex.findAll(lineStr).filter { it.value.isNotEmpty() }.toList()
                    if (hits.isNotEmpty()) {
                        totalReplaced += hits.size
                        materialized[i] = if (useRegex) {
                            regex.replace(lineStr, replacement)
                        } else {
                            regex.replace(lineStr) { replacement }
                        }
                    }
                }
                c.isDirty = true
            }
            if (totalReplaced > 0) {
                recomputeChunkStartLinesLocked(firstModifiedIdx.coerceAtLeast(0))
                currentVersionId = versionGen.incrementAndGet()
            }
        }
        return totalReplaced
    }

    fun saveToFile(destFile: File): Boolean {
        return try {
            destFile.parentFile?.mkdirs()
            val tmpFile = File(destFile.parentFile, ".${destFile.name}.saving.tmp")

            lock.write {
                val srcChannel = fileChannel
                val lastIdx = chunks.lastIndex
                FileOutputStream(tmpFile).use { fos ->
                    val outChannel = fos.channel
                    val bufferedOut = BufferedOutputStream(fos, 65536)
                    val writer = OutputStreamWriter(bufferedOut, Charsets.UTF_8)

                    for (cIdx in chunks.indices) {
                        val c = chunks[cIdx]
                        val memLines = c.lines
                        if (!c.isDirty && memLines == null && srcChannel != null) {
                            writer.flush()
                            bufferedOut.flush()
                            var transferred = 0L
                            val targetLen = c.byteLength.toLong()
                            var pos = c.fileOffset
                            while (transferred < targetLen) {
                                val t = srcChannel.transferTo(pos + transferred, targetLen - transferred, outChannel)
                                if (t <= 0) break
                                transferred += t
                            }
                            if (cIdx < lastIdx && !c.endsWithNewline) {
                                outChannel.write(ByteBuffer.wrap(byteArrayOf('\n'.code.toByte())))
                            }
                        } else {
                            val list = memLines ?: getChunkLinesLocked(c)
                            for (i in list.indices) {
                                writer.write(list[i])
                                val isVeryLastLine = (cIdx == lastIdx && i == list.lastIndex)
                                if (!isVeryLastLine) {
                                    writer.write("\n")
                                }
                            }
                        }
                    }
                    writer.flush()
                }

                // সেভ করার পর সোর্স চ্যানেল রিলোড করা
                val wasHuge = isHugeMode
                closeResourcesOnly()

                if (destFile.exists()) {
                    destFile.delete()
                }
                if (!tmpFile.renameTo(destFile)) {
                    tmpFile.copyTo(destFile, overwrite = true)
                    tmpFile.delete()
                }

                savedVersionId = currentVersionId
                if (wasHuge) {
                    val savedVer = savedVersionId
                    loadFromFile(destFile)
                    savedVersionId = savedVer
                    currentVersionId = savedVer
                } else {
                    sourceFileRef = destFile
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun splitLinesPreserveEmpty(text: String): ArrayList<String> {
        val result = ArrayList<String>()
        var pos = 0
        val len = text.length
        while (pos <= len) {
            val nl = text.indexOf('\n', pos)
            if (nl == -1) {
                result.add(text.substring(pos))
                break
            } else {
                result.add(text.substring(pos, nl))
                pos = nl + 1
            }
        }
        if (result.isEmpty()) result.add("")
        return result
    }
}