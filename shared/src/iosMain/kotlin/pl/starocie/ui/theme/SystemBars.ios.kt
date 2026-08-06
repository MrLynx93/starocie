package pl.starocie.ui.theme

import androidx.compose.runtime.Composable

/**
 * Nothing to do here yet.
 *
 * iOS takes the status bar's style from the hosting view controller rather than
 * from a window flag, and `MainViewController()` is handed straight to Swift — so
 * changing it means overriding `preferredStatusBarStyle` there, not from Compose.
 * The bar is legible on the light palette, which is where the app starts.
 */
@Composable
actual fun SystemBarsAppearance(dark: Boolean) = Unit
