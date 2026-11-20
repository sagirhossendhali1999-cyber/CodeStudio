package com.shd.codestudio.editorscreen

import android.content.Context
import com.shd.codestudio.homescreen.getProjectDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

data class ProjectSearchResult(
    val filePath: String,
    val lineNumber: Int,
    val lineText: String,
    val matchStartCol: Int,
    val matchEndCol: Int
)

data class ProjectSearchSummary(
    val results: List<ProjectSearchResult>,
    val fileCount: Int,
    val totalMatches: Int
)

object CodeSearchEngine {

    // বাইনারি, ইমেজ ও আর্কাইভ ফাইল ফিল্টার (এগুলো পড়লে মেমোরি জ্যাম হয়ে হ্যাং হতো)
    fun isBinaryOrArchiveFile(path: String): Boolean {
        val ext = path.substringAfterLast(".", "").lowercase()
        return ext in listOf(
            "png", "jpg", "jpeg", "webp", "gif", "bmp", "ico",
            "jar", "zip", "apk", "dex", "class", "so", "bin",
            "tar", "gz", "7z", "pdf", "ttf", "otf", "woff", "woff2",
            "mp3", "mp4", "wav", "ogg", "flat"
        )
    }

    fun buildRegex(
        query: String,
        matchCase: Boolean,
        wholeWord: Boolean,
        useRegex: Boolean
    ): Regex? {
        if (query.isBlank()) return null
        return try {
            val basePattern = if (useRegex) query else Regex.escape(query)
            val finalPattern = if (wholeWord) "\\b(?:$basePattern)\\b" else basePattern
            val options = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
            Regex(finalPattern, options)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun searchInProject(
        context: Context,
        projectName: String,
        allProjectFiles: List<String>,
        query: String,
        matchCase: Boolean,
        wholeWord: Boolean,
        useRegex: Boolean,
        maxResults: Int = 300
    ): ProjectSearchSummary = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext ProjectSearchSummary(emptyList(), 0, 0)
        }

        val rootDir = getProjectDirectory(context, projectName)
        val hits = ArrayList<ProjectSearchResult>()
        val matchedFiles = HashSet<String>()

        val isSimpleSearch = !useRegex && !wholeWord

        if (isSimpleSearch) {
            // অতি দ্রুত নেটিভ স্ট্রিং সার্চ (০% সিপিইউ ওভারহেড)
            for (relPath in allProjectFiles) {
                if (isBinaryOrArchiveFile(relPath)) continue
                val file = File(rootDir, relPath)
                if (!file.exists() || !file.isFile || file.length() > 1024 * 1024L) continue

                try {
                    BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8), 16384).use { reader ->
                        var lineNum = 1
                        var line = reader.readLine()
                        while (line != null && hits.size < maxResults) {
                            var idx = line.indexOf(query, 0, ignoreCase = !matchCase)
                            while (idx != -1 && hits.size < maxResults) {
                                matchedFiles.add(relPath)
                                hits.add(
                                    ProjectSearchResult(
                                        filePath = relPath,
                                        lineNumber = lineNum,
                                        lineText = line,
                                        matchStartCol = idx,
                                        matchEndCol = idx + query.length
                                    )
                                )
                                idx = line.indexOf(query, idx + query.length.coerceAtLeast(1), ignoreCase = !matchCase)
                            }
                            line = reader.readLine()
                            lineNum++
                        }
                    }
                } catch (_: Exception) { }

                yield() // অন্য থ্রেড যাতে ফ্রিজ না হয়
                if (hits.size >= maxResults) break
            }
        } else {
            val regex = buildRegex(query, matchCase, wholeWord, useRegex)
                ?: return@withContext ProjectSearchSummary(emptyList(), 0, 0)

            for (relPath in allProjectFiles) {
                if (isBinaryOrArchiveFile(relPath)) continue
                val file = File(rootDir, relPath)
                if (!file.exists() || !file.isFile || file.length() > 1024 * 1024L) continue

                try {
                    BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8), 16384).use { reader ->
                        var lineNum = 1
                        var line = reader.readLine()
                        while (line != null && hits.size < maxResults) {
                            for (match in regex.findAll(line)) {
                                if (match.value.isEmpty()) continue // জিরো-উইডথ রেজেক্স লুপ গার্ড
                                matchedFiles.add(relPath)
                                hits.add(
                                    ProjectSearchResult(
                                        filePath = relPath,
                                        lineNumber = lineNum,
                                        lineText = line,
                                        matchStartCol = match.range.first,
                                        matchEndCol = match.range.last + 1
                                    )
                                )
                                if (hits.size >= maxResults) break
                            }
                            line = reader.readLine()
                            lineNum++
                        }
                    }
                } catch (_: Exception) { }

                yield()
                if (hits.size >= maxResults) break
            }
        }

        ProjectSearchSummary(
            results = hits,
            fileCount = matchedFiles.size,
            totalMatches = hits.size
        )
    }

    suspend fun replaceAllInProjectFiles(
        context: Context,
        projectName: String,
        allProjectFiles: List<String>,
        query: String,
        replacement: String,
        matchCase: Boolean,
        wholeWord: Boolean,
        useRegex: Boolean
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext (0 to 0)

        val rootDir = getProjectDirectory(context, projectName)
        var totalReplacedCount = 0
        var totalFilesAffected = 0
        val isSimple = !useRegex && !wholeWord

        for (relPath in allProjectFiles) {
            if (isBinaryOrArchiveFile(relPath)) continue
            val file = File(rootDir, relPath)
            if (!file.exists() || !file.isFile || file.length() > 1024 * 1024L) continue

            try {
                val originalText = file.readText(Charsets.UTF_8)
                if (isSimple) {
                    if (originalText.contains(query, ignoreCase = !matchCase)) {
                        val replacedText = originalText.replace(query, replacement, ignoreCase = !matchCase)
                        if (replacedText != originalText) {
                            file.writeText(replacedText, Charsets.UTF_8)
                            totalFilesAffected++
                            totalReplacedCount++
                        }
                    }
                } else {
                    val regex = buildRegex(query, matchCase, wholeWord, useRegex) ?: continue
                    val matches = regex.findAll(originalText).filter { it.value.isNotEmpty() }.toList()
                    if (matches.isNotEmpty()) {
                        val replacedText = if (useRegex) {
                            regex.replace(originalText, replacement)
                        } else {
                            regex.replace(originalText) { replacement }
                        }
                        file.writeText(replacedText, Charsets.UTF_8)
                        totalReplacedCount += matches.size
                        totalFilesAffected++
                    }
                }
            } catch (_: Exception) { }

            yield() // সিপিইউ আটকে মোবাইল ফ্রিজ হওয়া ঠেকানোর জন্য
        }

        totalReplacedCount to totalFilesAffected
    }
}