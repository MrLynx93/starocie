package pl.starocie.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import pl.starocie.domain.LedgerRepository
import pl.starocie.domain.format
import pl.starocie.domain.sum

/**
 * Everything currently in stock, to browse rather than to sell.
 *
 * The sell screen is a search box — you already know what you are holding and you
 * are typing its name. This is the other question: what have we still got? Newest
 * first, because the thing you are least sure about is usually the thing you
 * bought last.
 *
 * Tapping a row opens it; selling and removing both happen there, one screen away
 * from the fast path where a stray tap costs money.
 */
@Composable
fun StockScreen(onOpenItem: (String) -> Unit, onDone: () -> Unit) {
    val repository: LedgerRepository = koinInject()
    val ledger by repository.ledger.collectAsState()

    val stock = remember(ledger) {
        ledger.itemsInStock().sortedByDescending { it.createdAt }
    }
    val stockValue = remember(stock) { stock.mapNotNull { it.price }.sum() }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Nasz magazyn", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Mamy tu ${rzeczy(stock.size)} · chcemy za nie ${stockValue.format()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        if (stock.isEmpty()) {
            Text(
                "Nic tu jeszcze nie mamy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(stock, key = { it.id }) { item ->
                    StockRow(item, onClick = { onOpenItem(item.id) })
                    HorizontalDivider()
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Gotowe") }
    }
}

/**
 * "1 rzeczy" is the kind of small wrongness that makes an app feel automated. Only
 * the singular differs in this word, so counting them out is one comparison.
 */
internal fun rzeczy(count: Int): String = if (count == 1) "1 rzecz" else "$count rzeczy"

/**
 * The same, for "przedmiot", which needs the full Polish rule: one takes the bare
 * word, a tail of 2–4 takes "przedmioty", and everything else "przedmiotów" — with
 * the teens carved out, because 12 counts like 5 and not like 2.
 */
internal fun przedmioty(count: Int): String {
    val tail = count % 10
    val teens = count % 100 in 12..14
    val word = when {
        count == 1 -> "przedmiot"
        tail in 2..4 && !teens -> "przedmioty"
        else -> "przedmiotów"
    }
    return "$count $word"
}
