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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import pl.starocie.domain.Item
import pl.starocie.domain.ItemStatus
import pl.starocie.domain.Ledger
import pl.starocie.domain.LedgerRepository
import pl.starocie.domain.Sell
import pl.starocie.domain.format

/**
 * One giełda: what we brought back from it and what went at it.
 *
 * The two lists are the magazyn's and the sold list's, narrowed to a day — same rows,
 * same wording, and a row still opens the thing, so the day is a way *into* the
 * records rather than a separate reading of them.
 *
 * They are two lists rather than one because a giełda is two different days' work at
 * once: what we bought there is still ours, and what we sold there was mostly bought
 * somewhere else. A thing can honestly appear in both.
 */
@Composable
fun SellingSessionDetailScreen(
    eventId: String,
    onOpenStockItem: (String) -> Unit,
    onOpenSoldItem: (String) -> Unit,
    onDone: () -> Unit,
) {
    val repository: LedgerRepository = koinInject()
    val ledger by repository.ledger.collectAsState()

    val event = ledger.eventById(eventId)
    val stats = remember(ledger, eventId) { event?.let { ledger.eventStats(it) } }

    // Everything that came in that day, newest first — an item belongs to a giełda
    // through its buy, which is the only place the link exists.
    val bought = remember(ledger, eventId) {
        ledger.buysOfEvent(eventId)
            .flatMap { ledger.itemsOfBuy(it.id) }
            .sortedByDescending { it.createdAt }
    }
    val sold = remember(ledger, eventId) {
        ledger.sellsOfEvent(eventId).sortedByDescending { it.createdAt }
    }

    ScreenColumn {
        Text(
            event?.name ?: event?.date?.toString() ?: "Nie znamy tej giełdy",
            style = MaterialTheme.typography.headlineSmall,
        )
        if (event?.name != null) {
            Text(
                event.date.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        stats?.let {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { SessionFigures(it) }
                Spacer(Modifier.width(12.dp))
                SessionProfit(it, style = MaterialTheme.typography.titleLarge)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (bought.isEmpty() && sold.isEmpty()) {
            Text(
                "Nic tu jeszcze nie ma.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (bought.isNotEmpty()) {
                    item { Column { SectionLabel("Co kupiliśmy") } }
                    items(bought, key = { "bought-${it.id}" }) { item ->
                        StockRow(
                            item = item,
                            stats = ledger.itemStats(item),
                            piecesLeft = ledger.piecesLeft(item),
                            onClick = openItemOrNull(item, onOpenStockItem, onOpenSoldItem) ?: {},
                        )
                        HorizontalDivider()
                    }
                }

                if (sold.isNotEmpty()) {
                    item {
                        Column {
                            Spacer(Modifier.height(if (bought.isEmpty()) 0.dp else 20.dp))
                            SectionLabel("Co sprzedaliśmy")
                        }
                    }
                    items(sold, key = { "sold-${it.id}" }) { sell ->
                        SessionSellRow(
                            sell = sell,
                            item = ledger.itemById(sell.itemId),
                            costLabel = sellCostLabel(ledger, sell),
                            onOpen = openItemOrNull(
                                ledger.itemById(sell.itemId),
                                onOpenStockItem,
                                onOpenSoldItem,
                            ),
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        BackButton(onDone)
    }
}

/**
 * One sale as it happened that day, rather than the item's whole story: this row
 * carries what this sale took and what those pieces had cost, so a lot that went
 * across three giełdy shows a third of itself at each.
 *
 * The item may be gone — deleting a thing leaves its sales unresolvable on purpose,
 * the proceeds still counting for the day. Then the row reads "—" and opens nothing,
 * which is the degrading-to-an-unknown every screen owes a deleted record.
 */
@Composable
private fun SessionSellRow(
    sell: Sell,
    item: Item?,
    costLabel: String,
    onOpen: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onOpen == null) Modifier else Modifier.clickable(onClick = onOpen))
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ItemThumb(item?.photo)
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(item?.name ?: "—", fontWeight = FontWeight.Medium)
            if (sell.quantity > 1) {
                Text(
                    // Above one it is a piece of a lot, so it is counted in sztuki —
                    // and the bare count avoids a verb that would have to agree with
                    // it as well ("poszły 3 sztuki", but "poszło 12 sztuk").
                    sztuki(sell.quantity),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                costLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(sell.price.format(), style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * What this sale's pieces had cost, said the same way [boughtForLabel] says it for a
 * whole thing — a share of a lot or of a box is a guess and says "ok.", and a thing
 * with no buy behind it says we do not know.
 */
private fun sellCostLabel(ledger: Ledger, sell: Sell): String {
    val cost = ledger.sellCost(sell) ?: return "nie wiemy, za ile kupiliśmy"
    return if (cost.isEstimated) {
        "kupiliśmy za ok. ${cost.cost.format()}"
    } else {
        "kupiliśmy za ${cost.cost.format()}"
    }
}

/**
 * A row opens the thing, and which screen that is depends on where the thing is now:
 * in stock it is still ours to price, sell and delete; sold it is a record with four
 * numbers left to correct. A deleted one, and a `REMOVED` one written before removing
 * became a delete, belong to neither and open nothing.
 */
private fun openItemOrNull(
    item: Item?,
    onOpenStockItem: (String) -> Unit,
    onOpenSoldItem: (String) -> Unit,
): (() -> Unit)? = when (item?.status) {
    ItemStatus.IN_STOCK -> ({ onOpenStockItem(item.id) })
    ItemStatus.SOLD -> ({ onOpenSoldItem(item.id) })
    else -> null
}
