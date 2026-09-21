package pl.starocie.ui

import androidx.compose.runtime.Composable

/**
 * Nothing to hook: iOS has no system back, and this app has no navigation
 * controller to swipe against either — every screen leaves by its own "Wstecz",
 * and the search closes with the × that is always on its line.
 */
@Composable
actual fun SystemBack(enabled: Boolean, onBack: () -> Unit) = Unit
