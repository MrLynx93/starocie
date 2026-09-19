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
import pl.starocie.domain.EventStats
import pl.starocie.domain.Item
import pl.starocie.domain.ItemStats
import pl.starocie.domain.ItemStatus
import pl.starocie.domain.LedgerRepository
import pl.starocie.domain.Money
import pl.starocie.domain.format

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
    val everything = remember(ledger) {
        ledger.items
            .filter { it.status == ItemStatus.SOLD }
            .map { it to ledger.itemStats(it) }
            .sortedByDescending { (item, stats) -> stats.soldAt ?: item.updatedAt }
    }
    val sold = remember(everything, query) {
        everything.filter { (item, _) -> query.isBlank() || item.matchesQuery(query) }
    }
    // Over everything sold, not over what the search found: the heading says what we
    // have sold, and looking for one thing does not change that.
    //
    // And over the *sales*, by way of the days, rather than over the rows below: the
    // rows are things wholly gone, and a lot of twelve plates is one of them while
    // being twelve things sold. Counting rows made this line — and the home card
    // that opens it — read smaller than the giełdy they are the total of, and left
    // a lot sold in part out altogether, pieces and money both, it being still in
    // the magazyn. It is [Ledger.overallStats], which is what the giełdy list and
    // the home screen's own third card read by, so the three cannot disagree.
    val all = remember(ledger) { ledger.overallStats() }

    ScreenColumn {
        Text("Co sprzedaliśmy", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Sprzedaliśmy ${przedmioty(all.itemsSold)} za ${all.earned.format()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            soldProfit(all),
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
            // A lot says how many of it went, in the words its own screen uses:
            // the heading above counts pieces, so a row standing for twelve of
            // them has to say twelve or the list will not add up to it.
            val went = if (stats.soldQuantity > 1) {
                "Sprzedaliśmy ${sztuki(stats.soldQuantity)} za ${stats.proceeds.format()}"
            } else {
                "Sprzedaliśmy za ${stats.proceeds.format()}"
            }
            Text(
                text = went + if (stats.sellCount > 1) " · w ${stats.sellCount} kawałkach" else "",
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
 * What everything we have sold made together.
 *
 * A thing we never recorded buying cost us nothing on the books, so the whole of what
 * it went for is in here — the same rule a giełda's profit follows, and the same one
 * its own row reads by. It is every day's profit summed, which is every sale set
 * against what its own pieces cost, so this line and the giełdy cannot answer
 * differently about the same afternoon.
 */
private fun soldProfit(stats: EventStats): String {
    val total = stats.profit
    // One share of a box anywhere makes the whole figure a guess.
    val approx = if (stats.profitIsEstimated) "ok. " else ""
    return if (total.minor < 0) {
        "Straciliśmy $approx${Money(-total.minor).format()}"
    } else {
        "Zarobiliśmy $approx${total.format()}"
    }
}
