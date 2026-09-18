package pl.starocie.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Where a screen's edges are, in one place.
 *
 * The app draws edge to edge, so a plain `fillMaxSize` column runs its heading
 * under the status bar and its last row under the gesture bar — which is exactly
 * what a list that scrolls perfectly well looks like when it seems to have run out.
 * [Scaffold] contributes the system-bar insets; `imePadding` handles the keyboard,
 * which covers the window rather than shrinking it, and would otherwise sit on top
 * of whatever is pinned at the bottom.
 *
 * The two insets are the same edge, though, and `consumeWindowInsets` is what stops
 * them being paid for twice: the keyboard is measured from the bottom of the screen
 * and so already covers the gesture bar, and without this the screen sits a gesture
 * bar's height above the keyboard — a strip of nothing under the last button, at
 * exactly the moment the list above it has the least room.
 *
 * The content is a [Column], so a screen keeps its usual shape: something with
 * `weight(1f)` scrolls, and everything after it stays put beneath.
 *
 * [bottom] is the one edge a screen may pull in: a list searched with the keyboard up
 * has the keyboard for a floor, and the usual margin there is rows it cannot show.
 */
@Composable
internal fun ScreenColumn(bottom: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = bottom),
            content = content,
        )
    }
}
