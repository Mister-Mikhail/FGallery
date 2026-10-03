package com.mistermikhail.fgallery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    background = Color(0xFF080B0F),
    surface = Color(0xFF080B0F),
    surfaceVariant = Color(0xFF151A21),
    primary = Color(0xFF79B8FF),
    onBackground = Color(0xFFF4F7FB),
    onSurface = Color(0xFFF4F7FB),
)

private val LightColors = lightColorScheme(
    background = Color(0xFFF3F1EC),
    surface = Color(0xFFF3F1EC),
    surfaceVariant = Color(0xFFE6E3DD),
    primary = Color(0xFF2868B7),
    onBackground = Color(0xFF121417),
    onSurface = Color(0xFF121417),
)

@Composable
fun FGalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
