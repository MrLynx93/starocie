package pl.starocie.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Light or dark, chosen here and not by the phone.
 *
 * The system setting is deliberately not consulted: a phone left on dark all day
 * for everything else still wants this bright, and a market stall in daylight is
 * exactly where the dark palette reads worst. So the app starts [LIGHT] and stays
 * wherever it is put.
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    ;

    val isDark: Boolean get() = this == DARK

    fun flipped(): ThemeMode = if (this == DARK) LIGHT else DARK
}

/** The current choice and the one thing you can do to it. */
@Stable
class ThemeChoice(val mode: ThemeMode, val toggle: () -> Unit)

/**
 * Remembers the choice across launches, on the device rather than in the
 * workspace: it is a preference about this phone's screen, not a fact about what
 * the two of us bought, and the other phone has its own.
 */
@Composable
expect fun rememberThemeChoice(): ThemeChoice

internal const val THEME_PREFS = "starocie.prefs"
internal const val THEME_KEY = "theme"

/** Anything unrecognised — or nothing stored yet — starts bright. */
internal fun themeModeOf(stored: String?): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.LIGHT
