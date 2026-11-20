package com.shd.codestudio.models

import android.net.Uri
import androidx.compose.ui.graphics.Color

data class Project(
    val name: String,
    val type: String,
    val color: Color,
    val isPinned: Boolean = false
)

data class ZipItem(
    val name: String,
    val uri: Uri,
    val size: Long,
    val date: Long
)

enum class ZipSort { 
    NEWEST, 
    OLDEST, 
    NAME_AZ, 
    LARGEST, 
    SMALLEST 
}

enum class ZipSizeFilter { 
    ALL, 
    SMALL, 
    MEDIUM, 
    LARGE 
}

data class FileTreeNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val children: MutableList<FileTreeNode> = mutableListOf(),
    val isPackage: Boolean = false,
    val compactedSegments: List<Pair<String, String>> = emptyList()
)

enum class SortType { 
    NAME_AZ, 
    NAME_ZA, 
    DATE_NEWEST, 
    DATE_OLDEST, 
    CUSTOM 
}