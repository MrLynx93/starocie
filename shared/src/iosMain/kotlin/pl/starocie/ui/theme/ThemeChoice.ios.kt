package pl.starocie.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSUserDefaults

/** The same one string, in the place iOS keeps such things. */
@Composable
actual fun rememberThemeChoice(): ThemeChoice {
    val defaults = remember { NSUserDefaults.standardUserDefaults }
    var mode by remember { mutableStateOf(themeModeOf(defaults.stringForKey(THEME_KEY))) }

    return ThemeChoice(mode) {
        val next = mode.flipped()
        defaults.setObject(next.name, THEME_KEY)
        mode = next
    }
}
