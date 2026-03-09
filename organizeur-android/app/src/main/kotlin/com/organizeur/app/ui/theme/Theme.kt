package com.organizeur.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback palette (should never be used since minSdk 31)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF8B6914),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF5E6D3),
    onPrimaryContainer = Color(0xFF3D2E0A),
    secondary = Color(0xFF6F5B3E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFAEBD7),
    onSecondaryContainer = Color(0xFF2A1F0A),
    tertiary = Color(0xFF5C6237),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE1E7B1),
    onTertiaryContainer = Color(0xFF1A1E00),
    surface = Color(0xFFFFFBF5),
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFF0E0CC),
    onSurfaceVariant = Color(0xFF4F4539),
    background = Color(0xFFFFFBF5),
    onBackground = Color(0xFF1E1B16),
    outline = Color(0xFF827567),
    outlineVariant = Color(0xFFD5C5B2),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF33302A),
    inverseOnSurface = Color(0xFFF8EFE5),
    inversePrimary = Color(0xFFE8C96E),
    surfaceTint = Color(0xFF8B6914)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE8C96E),
    onPrimary = Color(0xFF4A3600),
    primaryContainer = Color(0xFF6A5000),
    onPrimaryContainer = Color(0xFFF5E6D3),
    secondary = Color(0xFFD8C4A8),
    onSecondary = Color(0xFF3C2F15),
    secondaryContainer = Color(0xFF554428),
    onSecondaryContainer = Color(0xFFFAEBD7),
    tertiary = Color(0xFFC5CB97),
    onTertiary = Color(0xFF2E330D),
    tertiaryContainer = Color(0xFF454A21),
    onTertiaryContainer = Color(0xFFE1E7B1),
    surface = Color(0xFF16130E),
    onSurface = Color(0xFFEBE1D5),
    surfaceVariant = Color(0xFF4F4539),
    onSurfaceVariant = Color(0xFFD5C5B2),
    background = Color(0xFF16130E),
    onBackground = Color(0xFFEBE1D5),
    outline = Color(0xFF9D8F7E),
    outlineVariant = Color(0xFF4F4539),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFEBE1D5),
    inverseOnSurface = Color(0xFF33302A),
    inversePrimary = Color(0xFF8B6914),
    surfaceTint = Color(0xFFE8C96E)
)

@Composable
fun OrganizeurTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= 31) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) DarkColorScheme else LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
