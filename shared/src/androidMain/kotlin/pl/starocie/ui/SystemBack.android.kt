package pl.starocie.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/** The gesture or the button, whichever this phone is set to. */
@Composable
actual fun SystemBack(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
