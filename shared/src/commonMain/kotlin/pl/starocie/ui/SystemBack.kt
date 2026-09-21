package pl.starocie.ui

import androidx.compose.runtime.Composable

/**
 * The phone's own back, for a screen that has something to close before it leaves.
 *
 * Only the search uses it, and only while the search is open: back should put the
 * box away and give the heading back rather than leaving the screen with the list
 * still narrowed to something nobody can see they typed.
 *
 * The keyboard gets first refusal on Android — the IME swallows back before anything
 * in the app hears about it — so the presses fall out in the order somebody would
 * expect them to: the keyboard, then the search, then the screen. That is not
 * arranged here; it is what the platform already does, and [enabled] simply stops
 * this standing in the way when there is no search open.
 */
@Composable
expect fun SystemBack(enabled: Boolean, onBack: () -> Unit)
