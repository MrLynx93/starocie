package pl.starocie.ui.theme

import androidx.compose.runtime.Composable

/**
 * Tells the window which way the app is currently painted.
 *
 * The clock and the gesture bar are drawn by the system, and it picks their colour
 * from the *phone's* setting. The moment this app stopped following that setting,
 * a bright screen on a phone left in dark mode meant white icons on a white
 * background — so the choice has to be passed on rather than assumed.
 */
@Composable
expect fun SystemBarsAppearance(dark: Boolean)
