package com.shd.codestudio.editorscreen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.shd.codestudio.homescreen.getProjectDirectory
import com.shd.codestudio.models.FileTreeNode
import com.shd.codestudio.models.SortType
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class FlatFileNode(
    val node: FileTreeNode,
    val level: Int,
    val isLast: Boolean,
    val parentLines: List<Boolean>
)

enum class ClipboardAction { COPY, MOVE }

data class ClipboardState(
    val paths: List<String>,
    val action: ClipboardAction
)

enum class TreeViewMode {
    PROJECT,
    ANDROID
}

enum class DownloadFormat(val extension: String, val label: String) {
    ORIGINAL("", "Original File"),
    ZIP(".zip", "ZIP Archive (.zip)"),
    TXT(".txt", "Text Document (.txt)"),
    MD(".md", "Markdown (.md)")
}

private val SKIPPED_SCAN_DIRS = setOf(
    ".git", ".gradle", "build", ".kotlin", ".cxx", "captures", "node_modules", ".idea"
)

// ================= ১. ফাইল রিড ও সেভ =================
fun readFileContent(context: Context, projectName: String, filePath: String): String {
    return try {
        val file = File(getProjectDirectory(context, projectName), filePath)
        if (!file.exists() || !file.isFile) {
            "// File not found: $filePath"
        } else if (file.length() > 2 * 1024 * 1024L) {
            "// File is too large (${file.length() / 1024} KB) to open in quick reader."
        } else {
            file.readText(Charset.forName("UTF-8"))
        }
    } catch (e: Exception) {
        "// Error reading file: ${e.message}"
    }
}

fun saveFileContent(context: Context, projectName: String, filePath: String, content: String): Boolean {
    return try {
        val file = File(getProjectDirectory(context, projectName), filePath)
        file.parentFile?.mkdirs()
        file.writeText(content, Charset.forName("UTF-8"))
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

// ================= ২. পারসিস্ট্যান্ট প্রিফারেন্স =================
fun saveTreeViewMode(context: Context, mode: TreeViewMode) {
    try {
        val prefs = context.getSharedPreferences("CodeStudioPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("tree_view_mode", mode.name).apply()
    } catch (_: Exception) { }
}

fun loadTreeViewMode(context: Context): TreeViewMode {
    return try {
        val prefs = context.getSharedPreferences("CodeStudioPrefs", Context.MODE_PRIVATE)
        val name = prefs.getString("tree_view_mode", TreeViewMode.PROJECT.name)
        TreeViewMode.valueOf(name ?: TreeViewMode.PROJECT.name)
    } catch (_: Exception) {
        TreeViewMode.PROJECT
    }
}

fun saveCompactPackages(context: Context, compact: Boolean) {
    try {
        val prefs = context.getSharedPreferences("CodeStudioPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("compact_packages", compact).apply()
    } catch (_: Exception) { }
}

fun loadCompactPackages(context: Context): Boolean {
    return try {
        val prefs = context.getSharedPreferences("CodeStudioPrefs", Context.MODE_PRIVATE)
        prefs.getBoolean("compact_packages", true)
    } catch (_: Exception) {
        true
    }
}

fun saveTreeFontScale(context: Context, scale: Float) {
    try {
        val prefs = context.getSharedPreferences("CodeStudioPrefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("tree_font_scale", scale.coerceIn(0.6f, 1.65f)).apply()
    } catch (_: Exception) { }
}

fun loadTreeFontScale(context: Context): Float {
    return try {
        val prefs = context.getSharedPreferences("CodeStudioPrefs", Context.MODE_PRIVATE)
        prefs.getFloat("tree_font_scale", 1f).coerceIn(0.6f, 1.65f)
    } catch (_: Exception) {
        1f
    }
}

fun saveFileAbout(context: Context, projectName: String, filePath: String, aboutText: String) {
    try {
        val prefs = context.getSharedPreferences("CodeStudioFileAbout_$projectName", Context.MODE_PRIVATE)
        val trimmed = aboutText.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove("about_$filePath").apply()
        } else {
            prefs.edit().putString("about_$filePath", trimmed).apply()
        }
    } catch (_: Exception) { }
}

fun loadFileAbout(context: Context, projectName: String, filePath: String): String {
    return try {
        val prefs = context.getSharedPreferences("CodeStudioFileAbout_$projectName", Context.MODE_PRIVATE)
        prefs.getString("about_$filePath", "") ?: ""
    } catch (_: Exception) {
        ""
    }
}

fun getFilesWithAbout(context: Context, projectName: String): Set<String> {
    return try {
        val prefs = context.getSharedPreferences("CodeStudioFileAbout_$projectName", Context.MODE_PRIVATE)
        prefs.all.entries
            .filter { it.key.startsWith("about_") && (it.value as? String)?.isNotBlank() == true }
            .map { it.key.removePrefix("about_") }
            .toSet()
    } catch (_: Exception) {
        emptySet()
    }
}

fun formatLastEditDateTime(millis: Long): String {
    if (millis <= 0L) return "Unknown"
    return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(millis))
}

fun getAllProjectFiles(context: Context, projectName: String, maxFiles: Int = 500): List<String> {
    val rootDir = getProjectDirectory(context, projectName)
    if (!rootDir.exists()) return emptyList()

    val fileList = mutableListOf<String>()
    val queue = ArrayDeque<Triple<File, String, Int>>()
    queue.addLast(Triple(rootDir, "", 0))
    var visitedDirs = 0

    while (queue.isNotEmpty() && fileList.size < maxFiles && visitedDirs < 5000) {
        val (dir, currentPath, depth) = queue.removeFirst()
        visitedDirs++
        if (depth > 20) continue
        val files = dir.listFiles() ?: continue
        for (file in files) {
            if (fileList.size >= maxFiles) break
            val name = file.name
            if (file.isDirectory) {
                if (name in SKIPPED_SCAN_DIRS) continue
                val nextPath = if (currentPath.isEmpty()) name else "$currentPath/$name"
                queue.addLast(Triple(file, nextPath, depth + 1))
            } else {
                val path = if (currentPath.isEmpty()) name else "$currentPath/$name"
                fileList.add(path)
            }
        }
    }

    return fileList
}

fun getAllProjectFolderPathsBounded(context: Context, projectName: String, maxFolders: Int = 1000): List<String> {
    val rootDir = getProjectDirectory(context, projectName)
    if (!rootDir.exists()) return emptyList()

    val folders = mutableListOf<String>()
    val queue = ArrayDeque<Pair<File, String>>()
    queue.addLast(rootDir to "")

    while (queue.isNotEmpty() && folders.size < maxFolders) {
        val (dir, relPath) = queue.removeFirst()
        val children = dir.listFiles() ?: continue
        for (child in children) {
            if (!child.isDirectory) continue
            val name = child.name
            if (name.startsWith(".") || name in SKIPPED_SCAN_DIRS) continue
            val childRel = if (relPath.isEmpty()) name else "$relPath/$name"
            folders.add(childRel)
            if (folders.size >= maxFolders) break
            queue.addLast(child to childRel)
        }
    }
    return folders.sorted()
}

fun getCustomOrder(context: Context, projectName: String, folderPath: String): List<String> {
    return try {
        val prefs = context.getSharedPreferences("CodeStudioPrefs_$projectName", Context.MODE_PRIVATE)
        val orderStr = prefs.getString("order_$folderPath", "") ?: ""
        if (orderStr.isBlank()) emptyList() else orderStr.split("|")
    } catch (e: Exception) {
        emptyList()
    }
}

fun saveCustomOrder(context: Context, projectName: String, folderPath: String, order: List<String>) {
    try {
        val prefs = context.getSharedPreferences("CodeStudioPrefs_$projectName", Context.MODE_PRIVATE)
        prefs.edit().putString("order_$folderPath", order.joinToString("|")).apply()
    } catch (e: Exception) { }
}

fun saveSortType(context: Context, sortType: SortType) {
    try {
        val prefs = context.getSharedPreferences("CodeStudioPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("sort_type", sortType.name).apply()
    } catch (e: Exception) { }
}

fun loadSortType(context: Context): SortType {
    return try {
        val prefs = context.getSharedPreferences("CodeStudioPrefs", Context.MODE_PRIVATE)
        val name = prefs.getString("sort_type", SortType.NAME_AZ.name)
        SortType.valueOf(name ?: SortType.NAME_AZ.name)
    } catch (e: Exception) {
        SortType.NAME_AZ
    }
}

// ================= ৩. ট্রি জেনারেটর (10-15 GB প্রজেক্টের জন্য Lazy Loading) =================
private fun sortFileList(
    context: Context,
    projectName: String,
    rootDir: File,
    dir: File,
    files: List<File>,
    sortType: SortType
): List<File> {
    val relativeFolderPath = dir.relativeTo(rootDir).path
    return when (sortType) {
        SortType.NAME_AZ -> files.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        SortType.NAME_ZA -> files.sortedWith(compareBy<File> { !it.isDirectory }.thenByDescending { it.name.lowercase() })
        SortType.DATE_NEWEST -> files.sortedWith(compareBy<File> { !it.isDirectory }.thenByDescending { it.lastModified() })
        SortType.DATE_OLDEST -> files.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.lastModified() })
        SortType.CUSTOM -> {
            val customOrder = getCustomOrder(context, projectName, relativeFolderPath)
            files.sortedWith(
                compareBy<File> { !it.isDirectory }.thenBy {
                    val idx = customOrder.indexOf(it.name)
                    if (idx != -1) idx else Int.MAX_VALUE
                }.thenBy { it.name.lowercase() }
            )
        }
    }
}

fun getProjectFileTree(
    context: Context,
    projectName: String,
    sortType: SortType = SortType.NAME_AZ,
    showHidden: Boolean = false,
    expandedFolders: Set<String>? = null
): List<FileTreeNode> {
    val rootDir = getProjectDirectory(context, projectName)
    if (!rootDir.exists()) return emptyList()

    var totalNodesBuilt = 0
    val maxNodesLimit = 12_000
    val maxItemsPerFolder = 3000

    fun buildTree(dir: File, depth: Int): List<FileTreeNode> {
        if (depth > 25 || totalNodesBuilt >= maxNodesLimit) return emptyList()
        var files = dir.listFiles()?.toList() ?: return emptyList()

        if (!showHidden) {
            files = files.filter { !it.name.startsWith(".") }
        }

        val sortedFiles = sortFileList(context, projectName, rootDir, dir, files, sortType)
        val limitedFiles = if (sortedFiles.size > maxItemsPerFolder) {
            sortedFiles.take(maxItemsPerFolder)
        } else {
            sortedFiles
        }

        return limitedFiles.map { file ->
            totalNodesBuilt++
            val relPath = file.relativeTo(rootDir).path
            val node = FileTreeNode(
                name = file.name,
                path = relPath,
                isDirectory = file.isDirectory,
                size = if (file.isFile) file.length() else 0L,
                lastModified = file.lastModified()
            )
            // Lazy Loading: শুধুমাত্র এক্সপ্যান্ড করা ফোল্ডারগুলোর ভেতরের ফাইল লোড হবে
            if (file.isDirectory && (expandedFolders == null || expandedFolders.contains(relPath))) {
                node.children.addAll(buildTree(file, depth + 1))
            }
            node
        }
    }

    return buildTree(rootDir, 0)
}

fun searchProjectFileTree(
    context: Context,
    projectName: String,
    sortType: SortType,
    showHidden: Boolean,
    query: String,
    maxMatches: Int = 400
): Pair<List<FileTreeNode>, Int> {
    val rootDir = getProjectDirectory(context, projectName)
    if (!rootDir.exists() || query.isBlank()) return emptyList<FileTreeNode>() to 0

    var matchCount = 0
    var scannedCount = 0
    val maxScannedItems = 40_000

    fun searchRec(dir: File, depth: Int): List<FileTreeNode> {
        if (depth > 20 || matchCount >= maxMatches || scannedCount >= maxScannedItems) return emptyList()
        var files = dir.listFiles()?.toList() ?: return emptyList()
        if (!showHidden) {
            files = files.filter { !it.name.startsWith(".") }
        }
        val sortedFiles = sortFileList(context, projectName, rootDir, dir, files, sortType)
        val out = mutableListOf<FileTreeNode>()

        for (file in sortedFiles) {
            if (matchCount >= maxMatches || scannedCount >= maxScannedItems) break
            scannedCount++
            val isDir = file.isDirectory
            if (isDir && file.name in SKIPPED_SCAN_DIRS) continue

            val isMatch = file.name.contains(query, ignoreCase = true)
            if (isMatch) matchCount++

            val matchedKids = if (isDir) searchRec(file, depth + 1) else emptyList()
            if (isMatch || matchedKids.isNotEmpty()) {
                out.add(
                    FileTreeNode(
                        name = file.name,
                        path = file.relativeTo(rootDir).path,
                        isDirectory = isDir,
                        size = if (!isDir) file.length() else 0L,
                        lastModified = file.lastModified(),
                        children = matchedKids.toMutableList()
                    )
                )
            }
        }
        return out
    }

    val result = searchRec(rootDir, 0)
    return result to matchCount
}

fun getAndroidFileTree(
    context: Context,
    projectName: String,
    sortType: SortType = SortType.NAME_AZ,
    compactPackages: Boolean = true
): List<FileTreeNode> {
    val rootDir = getProjectDirectory(context, projectName)
    if (!rootDir.exists()) return emptyList()

    val appNode = FileTreeNode(name = "app", path = "app", isDirectory = true)
    val gradleScriptsNode = FileTreeNode(name = "Gradle Scripts", path = "__gradle_scripts__", isDirectory = true)

    val appDir = File(rootDir, "app")
    val srcMainDir = File(appDir, "src/main")

    // ১. Manifests
    val manifestFile = File(srcMainDir, "AndroidManifest.xml")
    if (manifestFile.exists()) {
        val manifestsNode = FileTreeNode(name = "manifests", path = "app/src/main/manifests", isDirectory = true)
        manifestsNode.children.add(
            FileTreeNode(
                name = "AndroidManifest.xml",
                path = manifestFile.relativeTo(rootDir).path,
                isDirectory = false,
                size = manifestFile.length(),
                lastModified = manifestFile.lastModified()
            )
        )
        appNode.children.add(manifestsNode)
    }

    var androidTreeNodes = 0
    val maxAndroidNodes = 12_000

    // ২. Kotlin / Java সোর্স ও টেস্ট প্যাকেজসমূহ
    fun buildPkgTree(dir: File, depth: Int): FileTreeNode {
        val relPath = dir.relativeTo(rootDir).path
        if (depth > 20 || androidTreeNodes >= maxAndroidNodes) {
            return FileTreeNode(
                name = dir.name,
                path = relPath,
                isDirectory = true,
                isPackage = true,
                compactedSegments = listOf(dir.name to relPath)
            )
        }
        val rawChildren = dir.listFiles()?.filter { !it.name.startsWith(".") } ?: emptyList()
        val subDirs = rawChildren.filter { it.isDirectory }.sortedBy { it.name.lowercase() }.take(500)
        val files = sortFileList(context, projectName, rootDir, dir, rawChildren.filter { it.isFile }, sortType).take(1500)

        val childNodes = mutableListOf<FileTreeNode>()
        subDirs.forEach {
            androidTreeNodes++
            childNodes.add(buildPkgTree(it, depth + 1))
        }
        files.forEach { f ->
            androidTreeNodes++
            childNodes.add(
                FileTreeNode(
                    name = f.name,
                    path = f.relativeTo(rootDir).path,
                    isDirectory = false,
                    size = f.length(),
                    lastModified = f.lastModified()
                )
            )
        }

        return FileTreeNode(
            name = dir.name,
            path = relPath,
            isDirectory = true,
            children = childNodes,
            isPackage = true,
            compactedSegments = listOf(dir.name to relPath)
        )
    }

    fun compactPackageNode(node: FileTreeNode): FileTreeNode {
        if (!node.isDirectory) return node
        val compactedChildren = node.children.map { compactPackageNode(it) }.toMutableList()
        var current = node.copy(children = compactedChildren)

        while (current.children.size == 1 && current.children[0].isDirectory) {
            val onlyChild = current.children[0]
            current = current.copy(
                name = "${current.name}.${onlyChild.name}",
                path = onlyChild.path,
                children = onlyChild.children,
                isPackage = true,
                compactedSegments = current.compactedSegments + onlyChild.compactedSegments
            )
        }
        return current
    }

    val mainKotlin = File(srcMainDir, "kotlin")
    val mainJava = File(srcMainDir, "java")
    val androidTestKotlin = File(appDir, "src/androidTest/kotlin")
    val androidTestJava = File(appDir, "src/androidTest/java")
    val unitTestKotlin = File(appDir, "src/test/kotlin")
    val unitTestJava = File(appDir, "src/test/java")

    val hasKotlin = mainKotlin.exists() || androidTestKotlin.exists() || unitTestKotlin.exists()
    val hasJava = mainJava.exists() || androidTestJava.exists() || unitTestJava.exists()

    val sourceSets = listOfNotNull(
        mainKotlin.takeIf { it.exists() }?.let { it to "" },
        mainJava.takeIf { it.exists() }?.let { it to "" },
        androidTestKotlin.takeIf { it.exists() }?.let { it to " (androidTest)" },
        androidTestJava.takeIf { it.exists() }?.let { it to " (androidTest)" },
        unitTestKotlin.takeIf { it.exists() }?.let { it to " (test)" },
        unitTestJava.takeIf { it.exists() }?.let { it to " (test)" }
    )

    if (sourceSets.isNotEmpty()) {
        val label = when {
            hasKotlin && hasJava -> "kotlin+java"
            hasKotlin -> "kotlin"
            else -> "java"
        }
        val primaryCodePath = sourceSets.first().first.relativeTo(rootDir).path
        val codePackageNode = FileTreeNode(name = label, path = primaryCodePath, isDirectory = true)

        sourceSets.forEach { (rootCodeDir, suffixLabel) ->
            val rawChildren = rootCodeDir.listFiles()?.filter { !it.name.startsWith(".") } ?: emptyList()
            val topDirs = rawChildren.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
            val topFiles = sortFileList(context, projectName, rootDir, rootCodeDir, rawChildren.filter { it.isFile }, sortType)

            topDirs.forEach { subDir ->
                val rawNode = buildPkgTree(subDir, 0)
                val finalNode = if (compactPackages) compactPackageNode(rawNode) else rawNode
                val labeledNode = if (suffixLabel.isNotEmpty()) {
                    finalNode.copy(name = "${finalNode.name}$suffixLabel")
                } else {
                    finalNode
                }
                codePackageNode.children.add(labeledNode)
            }
            topFiles.forEach { f ->
                codePackageNode.children.add(
                    FileTreeNode(
                        name = f.name,
                        path = f.relativeTo(rootDir).path,
                        isDirectory = false,
                        size = f.length(),
                        lastModified = f.lastModified()
                    )
                )
            }
        }
        appNode.children.add(codePackageNode)
    }

    // ৩. C++ Native সোর্স (cpp/) ও External Build Files (CMakeLists.txt)
    val cppDir = File(srcMainDir, "cpp")
    if (cppDir.exists()) {
        val cppFiles = cppDir.listFiles()
            ?.filter { it.isFile && !it.name.startsWith(".") && it.name != "CMakeLists.txt" }
            ?.let { sortFileList(context, projectName, rootDir, cppDir, it, sortType) }
            ?: emptyList()

        if (cppFiles.isNotEmpty()) {
            val cppNode = FileTreeNode(name = "cpp", path = cppDir.relativeTo(rootDir).path, isDirectory = true)
            cppFiles.forEach { f ->
                cppNode.children.add(
                    FileTreeNode(
                        name = f.name,
                        path = f.relativeTo(rootDir).path,
                        isDirectory = false,
                        size = f.length(),
                        lastModified = f.lastModified()
                    )
                )
            }
            appNode.children.add(cppNode)
        }

        val cmakeFile = File(cppDir, "CMakeLists.txt")
        if (cmakeFile.exists()) {
            val extBuildNode = FileTreeNode(name = "External Build Files", path = "__external_build__", isDirectory = true)
            extBuildNode.children.add(
                FileTreeNode(
                    name = "CMakeLists.txt",
                    path = cmakeFile.relativeTo(rootDir).path,
                    isDirectory = false,
                    size = cmakeFile.length(),
                    lastModified = cmakeFile.lastModified()
                )
            )
            appNode.children.add(extBuildNode)
        }
    }

    // ৪. Resources (res)
    val resDir = File(srcMainDir, "res")
    if (resDir.exists()) {
        val resNode = FileTreeNode(name = "res", path = resDir.relativeTo(rootDir).path, isDirectory = true)
        val subDirs = resDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") } ?: emptyList()

        val mipmapDirs = subDirs.filter { it.name.startsWith("mipmap") }.sortedBy { it.name }
        val valuesDirs = subDirs.filter { it.name == "values" || it.name.startsWith("values-") }.sortedBy { it.name }
        val otherResDirs = subDirs.filter { !it.name.startsWith("mipmap") && it.name != "values" && !it.name.startsWith("values-") }.sortedBy { it.name }

        otherResDirs.forEach { subRes ->
            val subResNode = FileTreeNode(name = subRes.name, path = subRes.relativeTo(rootDir).path, isDirectory = true)
            val resFiles = sortFileList(
                context, projectName, rootDir, subRes,
                subRes.listFiles()?.filter { it.isFile && !it.name.startsWith(".") } ?: emptyList(),
                sortType
            ).take(1500)
            resFiles.forEach { resFile ->
                subResNode.children.add(
                    FileTreeNode(
                        name = resFile.name,
                        path = resFile.relativeTo(rootDir).path,
                        isDirectory = false,
                        size = resFile.length(),
                        lastModified = resFile.lastModified()
                    )
                )
            }
            resNode.children.add(subResNode)
        }

        if (mipmapDirs.isNotEmpty()) {
            val mipmapNode = FileTreeNode(name = "mipmap", path = "__res_mipmap__", isDirectory = true)
            mipmapDirs.forEach { mDir ->
                val qualifier = mDir.name.removePrefix("mipmap").removePrefix("-")
                val mFiles = mDir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.sortedBy { it.name } ?: emptyList()
                mFiles.forEach { f ->
                    val disp = if (qualifier.isNotEmpty()) "${f.name} ($qualifier)" else f.name
                    mipmapNode.children.add(
                        FileTreeNode(
                            name = disp,
                            path = f.relativeTo(rootDir).path,
                            isDirectory = false,
                            size = f.length(),
                            lastModified = f.lastModified()
                        )
                    )
                }
            }
            resNode.children.add(mipmapNode)
        }

        if (valuesDirs.isNotEmpty()) {
            val primaryValuesDir = File(resDir, "values")
            val valuesPath = if (primaryValuesDir.exists()) primaryValuesDir.relativeTo(rootDir).path else valuesDirs.first().relativeTo(rootDir).path
            val valuesNode = FileTreeNode(name = "values", path = valuesPath, isDirectory = true)
            valuesDirs.forEach { vDir ->
                val qualifier = vDir.name.removePrefix("values").removePrefix("-")
                val vFiles = sortFileList(
                    context, projectName, rootDir, vDir,
                    vDir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") } ?: emptyList(),
                    sortType
                )
                vFiles.forEach { f ->
                    val disp = if (qualifier.isNotEmpty()) "${f.name} ($qualifier)" else f.name
                    valuesNode.children.add(
                        FileTreeNode(
                            name = disp,
                            path = f.relativeTo(rootDir).path,
                            isDirectory = false,
                            size = f.length(),
                            lastModified = f.lastModified()
                        )
                    )
                }
            }
            resNode.children.add(valuesNode)
        }

        resNode.children.sortBy { it.name.lowercase() }
        appNode.children.add(resNode)
    }

    // ৫. Gradle Scripts
    val gradleFiles = listOf(
        File(rootDir, "build.gradle.kts") to "build.gradle.kts (Project: $projectName)",
        File(rootDir, "app/build.gradle.kts") to "build.gradle.kts (Module :app)",
        File(rootDir, "app/proguard-rules.pro") to "proguard-rules.pro",
        File(rootDir, "gradle.properties") to "gradle.properties (Project Properties)",
        File(rootDir, "gradle/wrapper/gradle-wrapper.properties") to "gradle-wrapper.properties",
        File(rootDir, "gradle/libs.versions.toml") to "libs.versions.toml (Version Catalog)",
        File(rootDir, "settings.gradle.kts") to "settings.gradle.kts (Project Settings)"
    )

    gradleFiles.forEach { (file, display) ->
        if (file.exists()) {
            gradleScriptsNode.children.add(
                FileTreeNode(
                    name = display,
                    path = file.relativeTo(rootDir).path,
                    isDirectory = false,
                    size = file.length(),
                    lastModified = file.lastModified()
                )
            )
        }
    }

    return listOf(appNode, gradleScriptsNode)
}

fun moveFileTreeItem(context: Context, projectName: String, node: FileTreeNode, direction: Int): Boolean {
    return try {
        val rootDir = getProjectDirectory(context, projectName)
        val parentPath = File(node.path).parent ?: ""
        val parentDir = if (parentPath.isEmpty()) rootDir else File(rootDir, parentPath)
        val allItems = parentDir.listFiles()?.filter { !it.name.startsWith(".") } ?: return false

        val sameTypeItems = allItems.filter { it.isDirectory == node.isDirectory }
        val otherTypeItems = allItems.filter { it.isDirectory != node.isDirectory }

        val existingCustom = getCustomOrder(context, projectName, parentPath)
        val currentSameNames = sameTypeItems.map { it.name }.toSet()

        val orderedSame = if (existingCustom.isEmpty()) {
            sameTypeItems.sortedBy { it.name.lowercase() }.map { it.name }.toMutableList()
        } else {
            val fromCustom = existingCustom.filter { it in currentSameNames }
            val remaining = sameTypeItems.filter { it.name !in existingCustom }.sortedBy { it.name.lowercase() }.map { it.name }
            (fromCustom + remaining).toMutableList()
        }

        val index = orderedSame.indexOf(node.name)
        if (index == -1) return false

        val targetIndex = index + direction
        if (targetIndex !in orderedSame.indices) return false

        val temp = orderedSame[index]
        orderedSame[index] = orderedSame[targetIndex]
        orderedSame[targetIndex] = temp

        val otherOrdered = if (existingCustom.isEmpty()) {
            otherTypeItems.sortedBy { it.name.lowercase() }.map { it.name }
        } else {
            val otherNames = otherTypeItems.map { it.name }.toSet()
            val fromCustom = existingCustom.filter { it in otherNames }
            val remaining = otherTypeItems.filter { it.name !in existingCustom }.sortedBy { it.name.lowercase() }.map { it.name }
            fromCustom + remaining
        }

        val finalOrder = if (node.isDirectory) orderedSame + otherOrdered else otherOrdered + orderedSame
        saveCustomOrder(context, projectName, parentPath, finalOrder)
        saveSortType(context, SortType.CUSTOM)
        true
    } catch (e: Exception) {
        false
    }
}

fun flattenFileTree(
    rootNode: FileTreeNode,
    expandedFolders: Set<String>
): List<FlatFileNode> {
    val result = mutableListOf<FlatFileNode>()

    fun traverse(
        node: FileTreeNode,
        level: Int,
        isLast: Boolean,
        parentLines: List<Boolean>
    ) {
        result.add(FlatFileNode(node, level, isLast, parentLines))
        if (node.isDirectory && (level == 0 || expandedFolders.contains(node.path))) {
            val childParentLines = if (level == 0) listOf(false) else parentLines + listOf(!isLast)
            node.children.forEachIndexed { index, child ->
                traverse(
                    node = child,
                    level = level + 1,
                    isLast = index == node.children.lastIndex,
                    parentLines = childParentLines
                )
            }
        }
    }

    traverse(rootNode, 0, true, emptyList())
    return result
}

fun getAllFolderPaths(nodes: List<FileTreeNode>): Set<String> {
    val paths = mutableSetOf<String>()
    fun traverse(n: FileTreeNode) {
        if (paths.size >= 3000) return
        if (n.isDirectory) {
            if (n.path.isNotEmpty()) paths.add(n.path)
            n.children.forEach { traverse(it) }
        }
    }
    nodes.forEach { traverse(it) }
    return paths
}

// ================= ৪. ফাইল ও প্যাকেজ অপারেশন =================
fun validateFileName(name: String): String? {
    if (name.isBlank()) return "Name cannot be empty"
    if (name == "." || name == "..") return "Invalid name"
    if (name.contains(Regex("[\\\\/:*?\"<>|]"))) return "Name contains invalid characters"
    return null
}

fun validatePackageNameInput(pkg: String): String? {
    val trimmed = pkg.trim()
    if (trimmed.isBlank()) return "Package name cannot be empty"
    if (trimmed.startsWith(".") || trimmed.endsWith(".") || trimmed.contains("..")) return "Invalid dots in package"
    if (!trimmed.matches(Regex("[A-Za-z_][A-Za-z0-9_.]*"))) return "Use letters, numbers, underscores and dots"
    return null
}

fun createNewPackage(context: Context, projectName: String, parentPath: String, packageInput: String): Boolean {
    return try {
        val cleanPkg = packageInput.trim().trim('.')
        if (cleanPkg.isBlank()) return false
        val relSubPath = cleanPkg.replace(".", "/")
        val rootDir = getProjectDirectory(context, projectName)
        val parentDir = if (parentPath.isEmpty()) rootDir else File(rootDir, parentPath)
        val targetDir = File(parentDir, relSubPath)
        if (targetDir.exists()) return false
        targetDir.mkdirs()
    } catch (_: Exception) {
        false
    }
}

fun createNewItem(context: Context, projectName: String, parentPath: String, newName: String, isFolder: Boolean): Boolean {
    return try {
        val rootDir = getProjectDirectory(context, projectName)
        val parentDir = if (parentPath.isEmpty()) rootDir else File(rootDir, parentPath)
        val newFile = File(parentDir, newName)
        if (newFile.exists()) return false
        if (isFolder) {
            newFile.mkdirs()
        } else {
            newFile.parentFile?.mkdirs()
            newFile.createNewFile()

            val normalizedParent = parentPath.replace("\\", "/")
            val pkgPart = when {
                normalizedParent.contains("src/main/kotlin/") -> normalizedParent.substringAfter("src/main/kotlin/").replace("/", ".")
                normalizedParent.contains("src/main/java/") -> normalizedParent.substringAfter("src/main/java/").replace("/", ".")
                normalizedParent.contains("src/test/kotlin/") -> normalizedParent.substringAfter("src/test/kotlin/").replace("/", ".")
                normalizedParent.contains("src/test/java/") -> normalizedParent.substringAfter("src/test/java/").replace("/", ".")
                normalizedParent.contains("src/androidTest/kotlin/") -> normalizedParent.substringAfter("src/androidTest/kotlin/").replace("/", ".")
                normalizedParent.contains("src/androidTest/java/") -> normalizedParent.substringAfter("src/androidTest/java/").replace("/", ".")
                else -> ""
            }
            if (pkgPart.isNotBlank()) {
                if (newName.endsWith(".kt")) {
                    newFile.writeText("package $pkgPart\n\n")
                } else if (newName.endsWith(".java")) {
                    newFile.writeText("package $pkgPart;\n\n")
                }
            }
        }
        true
    } catch (e: Exception) {
        false
    }
}

fun renameItem(context: Context, projectName: String, oldPath: String, newName: String): Boolean {
    return try {
        val oldFile = File(getProjectDirectory(context, projectName), oldPath)
        val newFile = File(oldFile.parentFile, newName)
        if (newFile.exists()) return false
        val ok = oldFile.renameTo(newFile)
        if (ok && oldFile.isFile) {
            val oldAbout = loadFileAbout(context, projectName, oldPath)
            if (oldAbout.isNotEmpty()) {
                val rootDir = getProjectDirectory(context, projectName)
                val newRelPath = newFile.relativeTo(rootDir).path
                saveFileAbout(context, projectName, oldPath, "")
                saveFileAbout(context, projectName, newRelPath, oldAbout)
            }
        }
        ok
    } catch (e: Exception) {
        false
    }
}

fun deleteItem(context: Context, projectName: String, path: String): Boolean {
    return try {
        val file = File(getProjectDirectory(context, projectName), path)
        val ok = file.deleteRecursively()
        if (ok) saveFileAbout(context, projectName, path, "")
        ok
    } catch (e: Exception) {
        false
    }
}

fun deleteMultipleItems(context: Context, projectName: String, paths: List<String>): Boolean {
    return try {
        paths.forEach { path ->
            if (path.isNotEmpty()) {
                val file = File(getProjectDirectory(context, projectName), path)
                if (file.exists()) file.deleteRecursively()
                saveFileAbout(context, projectName, path, "")
            }
        }
        true
    } catch (e: Exception) {
        false
    }
}

fun pasteFile(context: Context, projectName: String, sourcePath: String, destFolderPath: String): Boolean {
    return pasteMultipleItems(context, projectName, listOf(sourcePath), destFolderPath, isMove = false) > 0
}

fun pasteMultipleItems(
    context: Context,
    projectName: String,
    sourcePaths: List<String>,
    destFolderPath: String,
    isMove: Boolean
): Int {
    var successCount = 0
    try {
        val rootDir = getProjectDirectory(context, projectName)
        val destFolder = if (destFolderPath.isEmpty()) rootDir else File(rootDir, destFolderPath)
        if (!destFolder.exists() || !destFolder.isDirectory) return 0
        val dstCanonical = destFolder.canonicalPath

        val cleanPaths = sourcePaths
            .filter { it.isNotEmpty() }
            .sortedBy { it.length }
            .fold(mutableListOf<String>()) { acc, path ->
                if (acc.none { parent -> path.startsWith("$parent/") }) {
                    acc.add(path)
                }
                acc
            }

        for (sourcePath in cleanPaths) {
            try {
                val sourceFile = File(rootDir, sourcePath)
                if (!sourceFile.exists()) continue

                val srcCanonical = sourceFile.canonicalPath
                if (sourceFile.isDirectory && (dstCanonical == srcCanonical || dstCanonical.startsWith("$srcCanonical${File.separator}"))) {
                    continue
                }

                if (isMove && sourceFile.parentFile?.canonicalPath == dstCanonical) {
                    continue
                }

                var finalDestFile = File(destFolder, sourceFile.name)
                var counter = 1
                while (finalDestFile.exists()) {
                    val baseName = if (sourceFile.isDirectory) sourceFile.name else sourceFile.name.substringBeforeLast(".")
                    val ext = if (!sourceFile.isDirectory && sourceFile.name.contains(".")) ".${sourceFile.name.substringAfterLast(".")}" else ""
                    val suffix = if (isMove) "_moved$counter" else "_copy$counter"
                    finalDestFile = File(destFolder, "$baseName$suffix$ext")
                    counter++
                }

                val copied = if (sourceFile.isDirectory) {
                    sourceFile.copyRecursively(finalDestFile, overwrite = true)
                } else {
                    sourceFile.copyTo(finalDestFile, overwrite = true)
                    true
                }

                if (copied) {
                    if (isMove) {
                        sourceFile.deleteRecursively()
                    }
                    successCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return successCount
}

// ================= ৫. কাস্টম ফরম্যাটে ফাইল ডাউনলোড =================
fun copyPathToClipboard(context: Context, projectName: String, itemPath: String) {
    try {
        val rootDir = getProjectDirectory(context, projectName)
        val file = if (itemPath.isEmpty()) rootDir else File(rootDir, itemPath)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("File Path", file.absolutePath)
        clipboard.setPrimaryClip(clip)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun shareFile(context: Context, projectName: String, filePath: String) {
    try {
        val file = File(getProjectDirectory(context, projectName), filePath)
        if (!file.exists()) return
        val shareText = if (file.isFile && !isImageFile(file.name) && file.length() <= 100 * 1024L) {
            "File: ${file.name}\nPath: $filePath\n\n${file.readText()}"
        } else {
            "File: ${file.name}\nPath: ${file.absolutePath}"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun downloadFileToStorageWithFormat(
    context: Context,
    projectName: String,
    filePath: String,
    format: DownloadFormat
): String? {
    return try {
        val sourceFile = File(getProjectDirectory(context, projectName), filePath)
        if (!sourceFile.exists()) return null

        val outFileName = if (sourceFile.isDirectory) {
            "${sourceFile.name}.zip"
        } else {
            when (format) {
                DownloadFormat.ORIGINAL -> sourceFile.name
                DownloadFormat.ZIP -> "${sourceFile.name}.zip"
                DownloadFormat.TXT -> "${sourceFile.name}.txt"
                DownloadFormat.MD -> "${sourceFile.name}.md"
            }
        }

        val mimeType = when {
            outFileName.endsWith(".zip") -> "application/zip"
            outFileName.endsWith(".txt") || outFileName.endsWith(".md") -> "text/plain"
            outFileName.endsWith(".xml") -> "text/xml"
            outFileName.endsWith(".png") -> "image/png"
            outFileName.endsWith(".jpg") -> "image/jpeg"
            else -> "application/octet-stream"
        }

        val buffer = ByteArray(131_072)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, outFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CodeStudio_Exports")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outStream ->
                    BufferedOutputStream(outStream, 131_072).use { bos ->
                        if (format == DownloadFormat.ZIP || sourceFile.isDirectory) {
                            ZipOutputStream(bos).use { zos ->
                                zipFileOrFolder(sourceFile, sourceFile.name, zos, buffer)
                            }
                        } else {
                            BufferedInputStream(sourceFile.inputStream(), 131_072).use { bis ->
                                var r: Int
                                while (bis.read(buffer).also { r = it } > 0) {
                                    bos.write(buffer, 0, r)
                                }
                            }
                        }
                    }
                }
                return "Downloads/CodeStudio_Exports/$outFileName"
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destFolder = File(downloadsDir, "CodeStudio_Exports").apply { if (!exists()) mkdirs() }
            val destFile = File(destFolder, outFileName)

            if (format == DownloadFormat.ZIP || sourceFile.isDirectory) {
                BufferedOutputStream(FileOutputStream(destFile), 131_072).use { bos ->
                    ZipOutputStream(bos).use { zos ->
                        zipFileOrFolder(sourceFile, sourceFile.name, zos, buffer)
                    }
                }
            } else {
                sourceFile.copyTo(destFile, overwrite = true)
            }
            return destFile.absolutePath
        }
        null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun downloadMultipleItemsWithFormat(
    context: Context,
    projectName: String,
    paths: List<String>,
    format: DownloadFormat
): Int {
    val rootDir = getProjectDirectory(context, projectName)
    val buffer = ByteArray(131_072)

    if (format == DownloadFormat.ZIP) {
        try {
            val zipName = "${projectName}_selected_${System.currentTimeMillis() % 10000}.zip"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, zipName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CodeStudio_Exports")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outStream ->
                        BufferedOutputStream(outStream, 131_072).use { bos ->
                            ZipOutputStream(bos).use { zos ->
                                paths.forEach { relPath ->
                                    val file = File(rootDir, relPath)
                                    if (file.exists()) zipFileOrFolder(file, file.name, zos, buffer)
                                }
                            }
                        }
                    }
                    return paths.size
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destFolder = File(downloadsDir, "CodeStudio_Exports").apply { if (!exists()) mkdirs() }
                val destFile = File(destFolder, zipName)
                BufferedOutputStream(FileOutputStream(destFile), 131_072).use { bos ->
                    ZipOutputStream(bos).use { zos ->
                        paths.forEach { relPath ->
                            val file = File(rootDir, relPath)
                            if (file.exists()) zipFileOrFolder(file, file.name, zos, buffer)
                        }
                    }
                }
                return paths.size
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
    }

    val filesToDownload = mutableSetOf<String>()
    fun collectFiles(file: File) {
        if (!file.exists()) return
        if (file.isDirectory) {
            file.listFiles()?.forEach { collectFiles(it) }
        } else {
            filesToDownload.add(file.relativeTo(rootDir).path)
        }
    }

    paths.forEach { relPath ->
        if (relPath.isNotEmpty()) collectFiles(File(rootDir, relPath))
    }

    var count = 0
    filesToDownload.forEach { relPath ->
        if (downloadFileToStorageWithFormat(context, projectName, relPath, format) != null) {
            count++
        }
    }
    return count
}

private fun zipFileOrFolder(source: File, entryPath: String, zos: ZipOutputStream, buffer: ByteArray) {
    if (source.isDirectory) {
        val children = source.listFiles() ?: return
        if (children.isEmpty()) {
            zos.putNextEntry(ZipEntry("$entryPath/"))
            zos.closeEntry()
        } else {
            children.forEach { child ->
                zipFileOrFolder(child, "$entryPath/${child.name}", zos, buffer)
            }
        }
    } else {
        zos.putNextEntry(ZipEntry(entryPath))
        BufferedInputStream(source.inputStream(), 131_072).use { bis ->
            var r: Int
            while (bis.read(buffer).also { r = it } > 0) {
                zos.write(buffer, 0, r)
            }
        }
        zos.closeEntry()
    }
}

// ================= ৬. প্রজেক্ট ইনফো হেল্পার =================
fun isImageFile(fileName: String): Boolean {
    val ext = fileName.substringAfterLast(".", "").lowercase()
    return ext in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
}

fun getPackageNameFromProject(context: Context, projectName: String): String {
    try {
        val gradleKts = readFileContent(context, projectName, "app/build.gradle.kts")
        val regexKts = "namespace\\s*=\\s*\"(.*?)\"".toRegex()
        regexKts.find(gradleKts)?.groupValues?.getOrNull(1)?.let { return it }

        val gradleGroovy = readFileContent(context, projectName, "app/build.gradle")
        val regexGroovy = "namespace\\s+['\"](.*?)['\"]".toRegex()
        regexGroovy.find(gradleGroovy)?.groupValues?.getOrNull(1)?.let { return it }

        val appIdRegex = "applicationId\\s*[=\\s]\\s*['\"](.*?)['\"]".toRegex()
        appIdRegex.find(gradleKts)?.groupValues?.getOrNull(1)?.let { return it }
    } catch (_: Exception) { }
    return "com.example.${projectName.lowercase().replace(" ", "")}"
}

fun getAppNameFromProject(context: Context, projectName: String): String {
    try {
        val stringsXml = readFileContent(context, projectName, "app/src/main/res/values/strings.xml")
        val regex = "<string\\s+name=\"app_name\">(.*?)</string>".toRegex()
        regex.find(stringsXml)?.groupValues?.getOrNull(1)?.let {
            if (it.isNotBlank()) return it
        }
    } catch (_: Exception) { }
    return projectName
}