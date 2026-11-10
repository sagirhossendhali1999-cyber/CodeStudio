package com.shd.codestudio

import android.net.Uri
import androidx.compose.ui.graphics.Color

// আগের মতো পিওর ব্ল্যাক এবং ডার্ক কালার
val DarkBackground = Color(0xFF0A0A0A)
val SurfaceDark = Color(0xFF161616)
val SurfaceLight = Color(0xFF222222)
val NeonLime = Color(0xFFD4FF33)
val TextPrimary = Color(0xFFF5F5F5)
val TextSecondary = Color(0xFF8B8B8B)

data class Project(
    val name: String,
    val type: String,
    val color: Color
)

data class ZipItem(
    val name: String, 
    val uri: Uri, 
    val size: Long, 
    val date: Long
)

enum class ZipSort { NEWEST, OLDEST, NAME_AZ, LARGEST, SMALLEST }
enum class ZipSizeFilter { ALL, SMALL, MEDIUM, LARGE }