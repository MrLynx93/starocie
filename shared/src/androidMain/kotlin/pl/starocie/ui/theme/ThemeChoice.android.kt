package pl.starocie.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** `SharedPreferences`, because one string does not need a database. */
@Composable
actual fun rememberThemeChoice(): ThemeChoice {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
    }
    var mode by remember { mutableStateOf(themeModeOf(prefs.getString(THEME_KEY, null))) }

    return ThemeChoice(mode) {
        val next = mode.flipped()
        // apply(), not commit(): the write is one string and nothing waits on it.
        prefs.edit().putString(THEME_KEY, next.name).apply()
        mode = next
    }
}
