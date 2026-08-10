package pl.starocie.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import pl.starocie.domain.Item
import pl.starocie.domain.ItemStats
import pl.starocie.domain.ItemStatus
import pl.starocie.domain.LedgerRepository
import pl.starocie.domain.Money
import pl.starocie.domain.format
import pl.starocie.domain.sum

/**
 * Everything that has already left as a sale, the counterpart to the magazyn list.
 *
 * The magazyn answers "what have we still got"; this one answers "what went, and
 * did it go for more than we paid". Each row carries the pair of numbers that makes
 * that judgement — what we gave and what we took — so the answer needs no tap.
 *
 * Only [ItemStatus.SOLD] things are here. A lot sold in part is still in stock and
 * stays in the other list, which is the same rule that keeps it out of the stats.
 * Removed things never sold, so they are in neither.
 */
@Composable
fun SoldScreen(onOpenItem: (String) -> Unit, onDone: () -> Unit) {
    val repository: LedgerRepository = koinInject()
    val ledger by repository.ledger.collectAsState()

    // The same in-memory search the magazyn does, for the same reason: a name is how
    // a thing is found, and by the time there are enough sales to be worth reading
    // there are too many to scroll.
    var query by remember { mutableStateOf("") }

    // Newest sale first: the thing you are least sure about is the thing that went
    // last. Items with no completed sale left by some other route, so they fall
    // back to when the record was last touched.
    val sold = remember(ledger, query) {
        ledger.items
            .filter { it.status == ItemStatus.SOLD }
            .filter { query.isBlank() || it.matchesQuery(query) }
            .map { it to ledger.itemStats(it) }
            .sortedByDescending { (item, stats) -> stats.soldAt ?: item.updatedAt }
    }
    // Over what is on screen, so a search answers for what it found.
    val proceeds = remember(sold) { sold.map { (_, stats) -> stats.proceeds }.sum() }
    val profit = remember(sold) { soldProfit(sold.map { (_, stats) -> stats }) }

    ScreenColumn {
        Text("Co sprzedaliśmy", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Sprzedaliśmy ${przedmioty(sold.size)} za ${proceeds.format()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            profit,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Czego szukasz?") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        if (sold.isEmpty()) {
            Text(
                if (query.isBlank()) "Jeszcze nic nie sprzedaliśmy." else "Nic takiego nie sprzedaliśmy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(sold, key = { (item, _) -> item.id }) { (item, stats) ->
                    SoldRow(item, stats) { onOpenItem(item.id) }
                    HorizontalDivider()
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        BackButton(onDone)
    }
}

/**
 * The whole row is one target, the way it is in the magazyn: it opens the thing, and
 * everything still correctable about it is there. What is on the row answers "was it
 * worth it" without the tap — the tap is for when one of the numbers is wrong.
 */
@Composable
private fun SoldRow(item: Item, stats: ItemStats, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ItemThumb(item.photo)
        Spacer(Modifier.width(12.dp))

        // What we gave and what we took, in the order they happened and one under
        // the other, so the two are read as a pair. A share of a box says "ok." —
        // a guess must never look like a measured price.
        Column(Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.Medium)
            Text(
                text = boughtForLabel(stats),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Sprzedaliśmy za ${stats.proceeds.format()}" +
                    if (stats.sellCount > 1) " · w ${stats.sellCount} kawałkach" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(12.dp))

        // The answer the list exists for, kept out of the pair it is drawn from: a
        // loss said as a loss rather than written as a negative gain.
        Column(horizontalAlignment = Alignment.End) {
            val profit = stats.profit
            val lost = stats.isALoss

            Text(
                text = if (lost) {
                    "${approx(stats)}${Money(-profit.minor).format()}"
                } else {
                    "${approx(stats)}${profit.format()}"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (lost) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = if (lost) "Straciliśmy" else "Zarobiliśmy",
                style = MaterialTheme.typography.bodySmall,
                color = if (lost) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun approx(stats: ItemStats) = if (stats.profitIsEstimated) "ok. " else ""

/**
 * What everything on screen made together.
 *
 * A thing we never recorded buying cost us nothing on the books, so the whole of what
 * it went for is in here — the same rule a giełda's profit follows, and the same one
 * its own row reads by.
 */
private fun soldProfit(stats: List<ItemStats>): String {
    val total = stats.map { it.profit }.sum()
    // One share of a box anywhere makes the whole figure a guess.
    val approx = if (stats.any { it.profitIsEstimated }) "ok. " else ""
    return if (total.minor < 0) {
        "Straciliśmy $approx${Money(-total.minor).format()}"
    } else {
        "Zarobiliśmy $approx${total.format()}"
    }
}
