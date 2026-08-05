package pl.starocie.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 * The content is a [Column], so a screen keeps its usual shape: something with
 * `weight(1f)` scrolls, and everything after it stays put beneath.
 */
@Composable
internal fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(20.dp),
            content = content,
        )
    }
}
