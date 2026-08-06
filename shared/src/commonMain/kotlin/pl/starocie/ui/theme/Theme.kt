package pl.starocie.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * A warm, muted palette — brass and patina rather than the default purple, which
 * reads as a generic Compose app. Only the key roles are set; Material derives the
 * rest.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5024),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC4),
    onPrimaryContainer = Color(0xFF2F1400),
    secondary = Color(0xFF4E6355),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E8D6),
    onSecondaryContainer = Color(0xFF0B1F14),
    tertiary = Color(0xFF3C6470),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC0E9F8),
    onTertiaryContainer = Color(0xFF001F27),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF221A14),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF221A14),
    surfaceVariant = Color(0xFFF3DFD1),
    onSurfaceVariant = Color(0xFF52443A),
    outline = Color(0xFF857469),
    error = Color(0xFFA33A32),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB784),
    onPrimary = Color(0xFF4E2600),
    primaryContainer = Color(0xFF6E3900),
    onPrimaryContainer = Color(0xFFFFDCC4),
    secondary = Color(0xFFB5CCBA),
    onSecondary = Color(0xFF213528),
    secondaryContainer = Color(0xFF374B3E),
    onSecondaryContainer = Color(0xFFD1E8D6),
    tertiary = Color(0xFFA4CDDC),
    onTertiary = Color(0xFF053541),
    background = Color(0xFF1A120C),
    onBackground = Color(0xFFF0DFD5),
    surface = Color(0xFF1A120C),
    onSurface = Color(0xFFF0DFD5),
    surfaceVariant = Color(0xFF52443A),
    onSurfaceVariant = Color(0xFFD7C3B5),
    outline = Color(0xFF9F8D81),
    error = Color(0xFFFFB4AB),
)

/**
 * [dark] comes from the app's own switch, never from `isSystemInDarkTheme()`: a
 * phone that lives in dark mode still wants this one bright at a stall in daylight.
 */
@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    SystemBarsAppearance(dark)
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
