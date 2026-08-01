package com.droidscp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

val Coral = Color(0xFFD97757)
val Cream = Color(0xFFF0EEE6)
val Ink = Color(0xFF1F1E1C)

private val Light = lightColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    secondary = Color(0xFF8A8577),
    background = Cream,
    onBackground = Ink,
    surface = Color(0xFFFAF9F5),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E4DA),
    onSurfaceVariant = Color(0xFF55524A)
)

private val Dark = darkColorScheme(
    primary = Coral,
    onPrimary = Color(0xFF1F1E1C),
    secondary = Color(0xFFB9B3A5),
    background = Color(0xFF171614),
    onBackground = Cream,
    surface = Color(0xFF201F1C),
    onSurface = Cream,
    surfaceVariant = Color(0xFF2C2A26),
    onSurfaceVariant = Color(0xFFC7C2B6)
)

@Composable
fun DroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography(
            titleLarge = TextStyle(fontSize = 20.sp),
            bodyMedium = TextStyle(fontSize = 14.sp)
        ),
        content = content
    )
}
