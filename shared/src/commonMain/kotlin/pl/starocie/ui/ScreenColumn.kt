package pl.starocie.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Where a screen's edges are, in one place.
 *
 * The app draws edge to edge, so a plain `fillMaxSize` column runs its heading under
 * the status bar and its last row under the gesture bar — which is exactly what a list
 * that scrolls perfectly well looks like when it seems to have run out. The keyboard is
 * the same edge again: it covers the window rather than shrinking it, so without it
 * being accounted for it sits on top of whatever is pinned at the bottom.
 *
 * **All three come from one place, and that is the whole point of this file.** It used
 * to be a `Scaffold` contributing the system bars and an `imePadding` contributing the
 * keyboard, with a `consumeWindowInsets` in between to stop the bottom edge being paid
 * for twice — three modifiers that had to agree, and on a phone with a three-button
 * navigation bar they did not: while the keyboard was up there was a navigation bar's
 * worth of nothing under the last button, at exactly the moment the list above it had
 * the least room. `safeDrawing` already unions the status bar, the navigation bar *and*
 * the keyboard, so taking it once is both shorter and incapable of disagreeing with
 * itself.
 *
 * [Surface] paints the theme's background behind it; the old `Scaffold` was doing that
 * as well, and it is the only other thing it was for.
 *
 * The content is a [Column], so a screen keeps its usual shape: something with
 * `weight(1f)` scrolls, and everything after it stays put beneath.
 *
 * [bottom] is the one edge a screen may pull in: a list searched with the keyboard up
 * has the keyboard for a floor, and the usual margin there is rows it cannot show.
 */
@Composable
internal fun ScreenColumn(bottom: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = bottom),
            content = content,
        )
    }
}
