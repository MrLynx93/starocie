package pl.starocie.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import pl.starocie.domain.LedgerRepository

/**
 * A write the server refused, said over whatever screen is open.
 *
 * Over every screen rather than on one, because the refusal arrives whenever the
 * server answers — a moment after the tap, or an hour later once the stall has signal
 * again — and by then the screen that made the change may be long gone. Firestore has
 * already rolled the change back, so without this the only sign is a record quietly
 * returning to what it was.
 *
 * At the top, so it never sits on the buttons pinned at the bottom of every screen,
 * and it stays until it is closed: it waits for nothing, it only has to be seen.
 */
@Composable
internal fun WriteErrorOverlay(content: @Composable () -> Unit) {
    val repository: LedgerRepository = koinInject()
    val error by repository.writeError.collectAsState()

    Box(Modifier.fillMaxSize()) {
        content()
        error?.let {
            WriteErrorBanner(
                message = it,
                onDismiss = repository::dismissWriteError,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun WriteErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp)) {
            Text("Nie udało się zapisać", style = MaterialTheme.typography.titleSmall)
            Text(
                "Baza nie przyjęła zmiany, więc wszystko wróciło do tego, co było.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            )
            // What the server actually said, small: it means nothing at a stall, and
            // it is the whole of what somebody fixing it needs.
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Zamknij")
            }
        }
    }
}
