package com.shd.codestudio.homescreen

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.shd.codestudio.models.Project
import com.shd.codestudio.models.ZipItem
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

data class ImportProgress(
    val filesCount: Int = 0,
    val bytesCopied: Long = 0L,
    val totalBytes: Long = 0L,
    val currentItem: String = ""
)

enum class ImportStatus {
    SUCCESS,
    CANCELLED,
    INSUFFICIENT_SPACE,
    ERROR
}

data class ImportResult(
    val status: ImportStatus,
    val projectName: String = "",
    val message: String = "",
    val filesCount: Int = 0,
    val bytesCopied: Long = 0L
)

private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
    @Volatile
    var bytesRead: Long = 0L
        private set

    override fun read(): Int {
        val b = super.read()
        if (b != -1) bytesRead++
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = super.read(b, off, len)
        if (count > 0) bytesRead += count
        return count
    }
}

// ================= ১. প্রজেক্ট লিস্ট সেভ ও লোড (Pin সাপোর্টসহ) =================
fun saveProjects(context: Context, projects: List<Project>) {
    try {
        val prefs = context.getSharedPreferences("CodeStudioPrefs", Context.MODE_PRIVATE)
        val text = projects.joinToString("\n") { "${it.name}|${it.type}|${it.isPinned}" }
        prefs.edit().putString("saved_projects", text).apply()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun loadProjects(context: Context): List<Project> {
    try {
        val prefs = context.getSharedPreferences("CodeStudioPrefs", Context.MODE_PRIVATE)
        val text = prefs.getString("saved_projects", "") ?: ""
        if (text.isBlank()) return emptyList()

        val list = mutableListOf<Project>()
        for (line in text.split("\n")) {
            if (line.isBlank()) continue
            val parts = line.split("|")
            if (parts.size >= 2) {
                val isPinned = if (parts.size >= 3) parts[2].toBoolean() else false
                list.add(Project(parts[0], parts[1], Color(0xFFA855F7), isPinned))
            }
        }
        return list
    } catch (e: Exception) {
        return emptyList()
    }
}

// ================= ২. স্টোরেজ পারমিশন ও জিপ ফাইল =================
fun hasStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

fun getAvailableStorageBytes(context: Context): Long {
    return try {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val stat = StatFs(baseDir.absolutePath)
        stat.availableBytes
    } catch (_: Exception) {
        Long.MAX_VALUE
    }
}

fun loadZipFiles(context: Context): List<ZipItem> {
    val list = mutableListOf<ZipItem>()
    val seen = mutableSetOf<String>()
    try {
        val collections = listOf(MediaStore.Files.getContentUri("external"), MediaStore.Downloads.getContentUri("external"))
        for (collection in collections) {
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )
            val selection = MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ?"
            val selectionArgs = arrayOf("%.zip")
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            try {
                context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameCol) ?: continue
                        if (!name.lowercase().endsWith(".zip")) continue
                        if (seen.contains(name)) continue
                        seen.add(name)
                        val id = cursor.getLong(idCol)
                        val size = try { cursor.getLong(sizeCol) } catch (e: Exception) { 0L }
                        val date = try { cursor.getLong(dateCol) } catch (e: Exception) { 0L }
                        val uri = ContentUris.withAppendedId(collection, id)
                        list.add(ZipItem(name, uri, size, date))
                    }
                }
            } catch (_: Exception) { }
        }
    } catch (_: Exception) { }
    return list.sortedByDescending { it.date }
}

fun formatSize(size: Long): String {
    return when {
        size < 1024L -> "$size B"
        size < 1024L * 1024L -> "${size / 1024L} KB"
        size < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
    }
}

fun formatDate(sec: Long): String {
    if (sec == 0L) return ""
    return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(sec * 1000L))
}

fun unzipFile(context: Context, zipUri: Uri, projectName: String): Boolean {
    return unzipFileStreaming(context, zipUri, projectName).status == ImportStatus.SUCCESS
}

fun unzipFileStreaming(
    context: Context,
    zipUri: Uri,
    projectName: String,
    totalZipBytes: Long = 0L,
    isCancelled: () -> Boolean = { false },
    onProgress: (ImportProgress) -> Unit = {}
): ImportResult {
    val destDir = getProjectDirectory(context, projectName)

    val avail = getAvailableStorageBytes(context)
    if (totalZipBytes > 0L && avail < totalZipBytes + 50L * 1024L * 1024L) {
        return ImportResult(
            status = ImportStatus.INSUFFICIENT_SPACE,
            projectName = projectName,
            message = "Not enough storage! Need > ${formatSize(totalZipBytes)}, available: ${formatSize(avail)}"
        )
    }

    var filesCount = 0
    var extractedBytes = 0L
    var lastReportTime = 0L

    return try {
        if (!destDir.exists()) destDir.mkdirs()
        val destCanonical = destDir.canonicalPath + File.separator

        val rawInput = context.contentResolver.openInputStream(zipUri)
            ?: return ImportResult(ImportStatus.ERROR, projectName, "Cannot open ZIP stream")

        val counting = CountingInputStream(rawInput)
        BufferedInputStream(counting, 131_072).use { bis ->
            ZipInputStream(bis).use { zis ->
                val buffer = ByteArray(131_072)
                var zipEntry = zis.nextEntry
                while (zipEntry != null) {
                    if (isCancelled()) {
                        destDir.deleteRecursively()
                        return ImportResult(ImportStatus.CANCELLED, projectName, "Import cancelled", filesCount, extractedBytes)
                    }

                    val safeEntryName = zipEntry.name.replace("\\", "/")
                    val newFile = File(destDir, safeEntryName)
                    val canonical = newFile.canonicalPath
                    if (canonical == destDir.canonicalPath || canonical.startsWith(destCanonical)) {
                        if (zipEntry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            newFile.parentFile?.mkdirs()
                            if (newFile.exists() && newFile.isDirectory) {
                                newFile.deleteRecursively()
                            }
                            BufferedOutputStream(FileOutputStream(newFile), 131_072).use { bos ->
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    if (isCancelled()) {
                                        bos.close()
                                        destDir.deleteRecursively()
                                        return ImportResult(ImportStatus.CANCELLED, projectName, "Import cancelled", filesCount, extractedBytes)
                                    }
                                    bos.write(buffer, 0, len)
                                    extractedBytes += len

                                    val now = System.currentTimeMillis()
                                    if (now - lastReportTime >= 120L) {
                                        lastReportTime = now
                                        val progressBytes = if (totalZipBytes > 0L) counting.bytesRead.coerceAtMost(totalZipBytes) else extractedBytes
                                        onProgress(
                                            ImportProgress(
                                                filesCount = filesCount,
                                                bytesCopied = progressBytes,
                                                totalBytes = totalZipBytes,
                                                currentItem = safeEntryName.substringAfterLast("/")
                                            )
                                        )
                                    }
                                }
                            }
                            filesCount++
                        }
                    }

                    zis.closeEntry()
                    zipEntry = zis.nextEntry

                    val now = System.currentTimeMillis()
                    if (now - lastReportTime >= 120L) {
                        lastReportTime = now
                        val progressBytes = if (totalZipBytes > 0L) counting.bytesRead.coerceAtMost(totalZipBytes) else extractedBytes
                        onProgress(
                            ImportProgress(
                                filesCount = filesCount,
                                bytesCopied = progressBytes,
                                totalBytes = totalZipBytes,
                                currentItem = safeEntryName.substringAfterLast("/")
                            )
                        )
                    }
                }
            }
        }

        ImportResult(
            status = ImportStatus.SUCCESS,
            projectName = projectName,
            filesCount = filesCount,
            bytesCopied = extractedBytes
        )
    } catch (e: IOException) {
        e.printStackTrace()
        destDir.deleteRecursively()
        val msg = e.message ?: "I/O error during unzip"
        val isNoSpace = msg.contains("ENOSPC", ignoreCase = true) || msg.contains("space", ignoreCase = true)
        ImportResult(
            status = if (isNoSpace) ImportStatus.INSUFFICIENT_SPACE else ImportStatus.ERROR,
            projectName = projectName,
            message = if (isNoSpace) "Storage full while extracting ZIP!" else msg
        )
    } catch (e: Exception) {
        e.printStackTrace()
        destDir.deleteRecursively()
        ImportResult(ImportStatus.ERROR, projectName, e.message ?: "Failed to extract archive")
    }
}

// ================= ২.৫. ফাস্ট স্ট্রিমিং ফোল্ডার ইমপোর্ট =================
fun importFolderStreaming(
    context: Context,
    treeUri: Uri,
    isCancelled: () -> Boolean = { false },
    onProgress: (ImportProgress) -> Unit = {}
): ImportResult {
    val contentResolver = context.contentResolver
    try {
        contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: Exception) { }

    val treeDocId = try {
        DocumentsContract.getTreeDocumentId(treeUri)
    } catch (_: Exception) {
        null
    }

    val rawName = DocumentFile.fromTreeUri(context, treeUri)?.name?.trim() ?: "FolderProject"
    val projectName = rawName.ifEmpty { "FolderProject_${System.currentTimeMillis() % 1000}" }
    val targetDir = getProjectDirectory(context, projectName)

    if (treeDocId == null) {
        return ImportResult(ImportStatus.ERROR, projectName, "Invalid folder URI")
    }

    var filesCount = 0
    var bytesCopied = 0L
    var lastReportTime = 0L
    var lastSpaceCheckBytes = 0L

    return try {
        if (!targetDir.exists()) targetDir.mkdirs()

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        val stack = ArrayDeque<Pair<String, File>>()
        stack.addLast(treeDocId to targetDir)
        val buffer = ByteArray(131_072)

        while (stack.isNotEmpty()) {
            if (isCancelled()) {
                targetDir.deleteRecursively()
                return ImportResult(ImportStatus.CANCELLED, projectName, "Import cancelled", filesCount, bytesCopied)
            }

            val (dirDocId, destFolder) = stack.removeLast()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)

            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    if (isCancelled()) {
                        targetDir.deleteRecursively()
                        return ImportResult(ImportStatus.CANCELLED, projectName, "Import cancelled", filesCount, bytesCopied)
                    }

                    val childId = cursor.getString(idCol) ?: continue
                    val childName = cursor.getString(nameCol) ?: continue
                    val mimeType = cursor.getString(mimeCol) ?: ""

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val subDir = File(destFolder, childName)
                        if (!subDir.exists()) subDir.mkdirs()
                        stack.addLast(childId to subDir)
                    } else {
                        val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        val destFile = File(destFolder, childName)
                        try {
                            contentResolver.openInputStream(childUri)?.use { input ->
                                BufferedOutputStream(FileOutputStream(destFile), 131_072).use { output ->
                                    var len: Int
                                    while (input.read(buffer).also { len = it } > 0) {
                                        if (isCancelled()) {
                                            output.close()
                                            targetDir.deleteRecursively()
                                            return ImportResult(ImportStatus.CANCELLED, projectName, "Import cancelled", filesCount, bytesCopied)
                                        }
                                        output.write(buffer, 0, len)
                                        bytesCopied += len

                                        val now = System.currentTimeMillis()
                                        if (now - lastReportTime >= 120L) {
                                            lastReportTime = now
                                            onProgress(
                                                ImportProgress(
                                                    filesCount = filesCount,
                                                    bytesCopied = bytesCopied,
                                                    totalBytes = 0L,
                                                    currentItem = childName
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            filesCount++
                            if (bytesCopied - lastSpaceCheckBytes >= 100L * 1024L * 1024L) {
                                lastSpaceCheckBytes = bytesCopied
                                if (getAvailableStorageBytes(context) < 50L * 1024L * 1024L) {
                                    targetDir.deleteRecursively()
                                    return ImportResult(
                                        ImportStatus.INSUFFICIENT_SPACE,
                                        projectName,
                                        "Storage almost full! Import stopped safely."
                                    )
                                }
                            }
                        } catch (ioEx: IOException) {
                            val msg = ioEx.message ?: ""
                            if (msg.contains("ENOSPC", ignoreCase = true) || msg.contains("space", ignoreCase = true)) {
                                targetDir.deleteRecursively()
                                return ImportResult(ImportStatus.INSUFFICIENT_SPACE, projectName, "Device storage full!")
                            }
                        } catch (_: Exception) { }
                    }
                }
            }
        }

        ImportResult(
            status = ImportStatus.SUCCESS,
            projectName = projectName,
            filesCount = filesCount,
            bytesCopied = bytesCopied
        )
    } catch (e: Exception) {
        e.printStackTrace()
        targetDir.deleteRecursively()
        ImportResult(ImportStatus.ERROR, projectName, e.message ?: "Failed to import folder")
    }
}

// ================= ৩. Assets ও প্রজেক্ট ডিরেক্টরি =================
fun copyAssetToFile(context: Context, assetPath: String, destFile: File) {
    try {
        val inputStream: InputStream = context.assets.open(assetPath)
        destFile.parentFile?.mkdirs()
        FileOutputStream(destFile).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        inputStream.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun getProjectDirectory(context: Context, projectName: String): File {
    val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
    val rootDir = File(baseDir, "CodeStudioProjects")
    if (!rootDir.exists()) rootDir.mkdirs()
    return File(rootDir, projectName)
}