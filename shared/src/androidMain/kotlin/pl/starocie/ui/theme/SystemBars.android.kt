package pl.starocie.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun SystemBarsAppearance(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = view.context.findActivity()?.window ?: return

    SideEffect {
        // "Light bars" means dark icons for a light background, which is the
        // opposite of what the word suggests and the source of the confusion.
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

/**
 * The composition's context is not always the activity — `setContent` wraps it —
 * so unwrap rather than casting and hoping.
 */
private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
