package com.shd.codestudio

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF0A0A0A)
val SurfaceDark = Color(0xFF161616)
val SurfaceLight = Color(0xFF222222)
val NeonLime = Color(0xFFD4FF33)
val TextPrimary = Color(0xFFF5F5F5)
val TextSecondary = Color(0xFF8B8B8B)

val RootColor = Color(0xFFFF5252)
val FolderColor = Color(0xFF00E5FF)
val FileColor = Color(0xFFF5F5F5)
val TreeLineColor = Color(0xFF9E9E9E)
val PurpleHeader = Color(0xFFA855F7)

val EditorNeonLime = Color(0xFFD4FF33)
val EditorTextSecondary = Color(0xFF8B8B8B)
val EditorBackground = Color(0xFF0A0A0A)
val EditorSurfaceDark = Color(0xFF1E1E1E)

private val CodeStudioColorScheme = darkColorScheme(
    primary = NeonLime,
    onPrimary = Color.Black,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary
)

@Composable
fun CodeStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CodeStudioColorScheme,
        content = content
    )
}