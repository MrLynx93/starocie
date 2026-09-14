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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import pl.starocie.domain.CurrentEventResolver
import pl.starocie.domain.Item
import pl.starocie.domain.ItemStatus
import pl.starocie.domain.LedgerRepository
import pl.starocie.domain.Money
import pl.starocie.domain.SaleGroup
import pl.starocie.domain.SellCost
import pl.starocie.domain.format
import pl.starocie.domain.saleGroups

/**
 * One day: what we brought back from it and what went at it.
 *
 * Usually a giełda, and sometimes a day we only shopped on. The screen is the same
 * either way: a day is a day, and the difference is only that one of its two sections
 * is empty.
 *
 * The two lists are the magazyn's and the sold list's, narrowed to a day — same rows,
 * same wording, and a row still opens the thing, so the day is a way *into* the
 * records rather than a separate reading of them.
 *
 * They are two lists rather than one because a giełda is two different days' work at
 * once: what we bought there is still ours, and what we sold there was mostly bought
 * somewhere else. A thing can honestly appear in both.
 *
 * One search box sits under the day's figures and narrows both sections, the way the
 * magazyn, the sold list and the giełdy list are each searched — a day worth reading
 * is a day too long to scroll. The figures above it stay the day's own.
 *
 * The day being *today* changes one thing: a sale started from here would be dated
 * today and counted in today's takings, which is the whole reason a day that has been
 * and gone offers no "Sprzedaj". When the day on screen is the one every write
 * resolves to, that objection is gone and the button belongs — this is the giełda we
 * are standing at. What is sold at a giełda is mostly what we bought at some other
 * one, so the thing a buyer is holding is usually nowhere in these two lists; without
 * that button, selling from the stall we are standing at meant going back out to the
 * home screen, the one place the whole magazyn can be searched.
 *
 * It is the *only* button, and a thing that was never recorded is reached through it
 * rather than beside it: "Sprzedaj" opens the magazyn, and the search that fails to
 * find the thing is what offers "Dodaj … i sprzedaj" there. A second button here
 * would put the rarer of the two moments alongside the commoner one on a screen that
 * is for reading a day.
 */
@OptIn(ExperimentalTime::class)
@Composable
fun SessionDetailScreen(
    eventId: String,
    onOpenStockItem: (itemId: String, selling: Boolean) -> Unit,
    onOpenSoldItem: (String) -> Unit,
    onSell: () -> Unit,
    onDone: () -> Unit,
) {
    val repository: LedgerRepository = koinInject()
    val ledger by repository.ledger.collectAsState()

    val scope = rememberCoroutineScope()
    val event = ledger.eventById(eventId)

    // Asked once, when the day is opened: a screen left open across midnight would
    // still offer the button, and the sale it wrote would go to the new day's event
    // rather than this one. Reopening the giełda is what puts that right, and a phone
    // that sat unlocked at a stall through midnight is not the case to complicate
    // this for.
    val sellingToday = remember(eventId) {
        CurrentEventResolver().isCurrent(eventId, Clock.System.now())
    }

    // Seeded once the day actually arrives, and left alone after: following the
    // ledger into the field would fight the keyboard, since every write comes back.
    var name by remember(eventId) { mutableStateOf("") }
    LaunchedEffect(event?.id) { event?.let { name = it.name.orEmpty() } }
    val stats = remember(ledger, eventId) { event?.let { ledger.eventStats(it) } }

    // Everything that came in that day, newest first — an item belongs to a giełda
    // through its buy, which is the only place the link exists.
    val boughtThatDay = remember(ledger, eventId) {
        ledger.buysOfEvent(eventId)
            .flatMap { ledger.itemsOfBuy(it.id) }
            .sortedByDescending { it.createdAt }
    }
    val soldThatDay = remember(ledger, eventId) {
        ledger.sellsOfEvent(eventId).sortedByDescending { it.createdAt }
    }

    // The same search the other three lists carry, in the same words: a good giełda
    // is a hundred rows across the two sections, and typing a name is how anything
    // is found in this app. It filters both sections at once, because a thing bought
    // and sold on the same day honestly appears in each and one box must find it in
    // both.
    var query by remember(eventId) { mutableStateOf("") }
    val bought = remember(boughtThatDay, query) {
        boughtThatDay.filter { query.isBlank() || it.matchesQuery(query) }
    }
    // A sale is found by the thing it was, so it is the item's name that is matched.
    // A sale whose item has been deleted has no name left to match and drops out of
    // a search — it is still there, unsearched, the moment the box is cleared.
    //
    // Then the repeats collapse: ten rings rung up one at a time at 15,00 zł are one
    // line reading "10 sztuk", not ten identical rows to scroll past.
    val sold = remember(ledger, soldThatDay, query) {
        ledger.saleGroups(
            soldThatDay.filter {
                query.isBlank() || ledger.itemById(it.itemId)?.matchesQuery(query) == true
            },
        )
    }

    ScreenColumn {
        if (event == null) {
            Text("Nie znamy tej giełdy", style = MaterialTheme.typography.headlineSmall)
        } else {
            // Named after the fact, usually: the day exists the moment anything is
            // recorded, and what to call it is remembered on the way home.
            NameField(
                label = "Nazwa giełdy",
                text = name,
                onTextChange = { name = it },
                saved = event.name.orEmpty(),
                placeholder = event.date.asText(),
                onSave = { scope.launch { repository.nameEvent(eventId, it) } },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                event.date.asText(),
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

        // Below the day's figures rather than above them, and that is the whole
        // reason they are not computed over what the search found: they answer for
        // the giełda, which is what the row in the list behind this screen says too,
        // and the two must not disagree because somebody is looking for a lamp.
        if (boughtThatDay.isNotEmpty() || soldThatDay.isNotEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Czego szukasz?") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
        }

        if (bought.isEmpty() && sold.isEmpty()) {
            Text(
                if (query.isBlank()) "Nic tu jeszcze nie ma." else "Nic takiego tu nie ma.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                // Selling is what a giełda is for, so it leads. What we carried
                // home follows, and is read as the day's other half.
                if (sold.isNotEmpty()) {
                    item { Column { SectionLabel("Co sprzedaliśmy") } }
                    items(sold, key = { "sold-${it.sells.first().id}" }) { group ->
                        SessionSellRow(
                            group = group,
                            // A collapsed line opens its newest sale's thing: one of
                            // the ten is as good as another to correct, and its own
                            // "Cofnij sprzedaż" takes back exactly one of them.
                            onOpen = openItemOrNull(
                                group.item,
                                sellingToday,
                                onOpenStockItem,
                                onOpenSoldItem,
                            ),
                        )
                        HorizontalDivider()
                    }
                }

                if (bought.isNotEmpty()) {
                    item {
                        Column {
                            Spacer(Modifier.height(if (sold.isEmpty()) 0.dp else 20.dp))
                            SectionLabel("Co kupiliśmy")
                        }
                    }
                    items(bought, key = { "bought-${it.id}" }) { item ->
                        StockRow(
                            item = item,
                            stats = ledger.itemStats(item),
                            piecesLeft = ledger.piecesLeft(item),
                            onClick = openItemOrNull(
                                item,
                                sellingToday,
                                onOpenStockItem,
                                onOpenSoldItem,
                            ) ?: {},
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Only on the day itself, and for the same reason the rows keep "Sprzedaj":
        // what it writes is a sale dated today, which on any other giełda would put
        // the money in a day nobody was reading.
        if (sellingToday && event != null) {
            // The magazyn, opened to sell from: these two sections are a day's own
            // work, and the thing being handed over was most likely bought at some
            // other giełda entirely. It is the same button the home screen leads with,
            // in the same word, landing on the same searchable list.
            //
            // It is the only button here, and the list it opens is where the thing
            // that was never recorded is added — the sell list's own "Dodaj … i
            // sprzedaj" is one tap further on, from the screen whose search box has
            // just failed to find it. Offering that door twice put a second primary
            // decision on a day's screen for the rarer of the two moments.
            Button(
                onClick = onSell,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Sprzedaj", fontWeight = FontWeight.Medium) }

            Spacer(Modifier.height(10.dp))
        }

        BackButton(onDone)
    }
}

/**
 * One sale as it happened that day — or several that were the same thing sold again
 * ([Ledger.saleGroups]) — rather than the item's whole story: this row carries what
 * those sales took and what their pieces had cost, so a lot that went across three
 * giełdy shows a third of itself at each.
 *
 * The item may be gone — deleting a thing leaves its sales unresolvable on purpose,
 * the proceeds still counting for the day. Then the row reads "—" and opens nothing,
 * which is the degrading-to-an-unknown every screen owes a deleted record.
 */
@Composable
private fun SessionSellRow(
    group: SaleGroup,
    onOpen: (() -> Unit)?,
) {
    val item = group.item
    val cost = group.cost
    // Summed from [Ledger.sellProfit] — the one place per-sale profit is worked out.
    val profit = group.profit
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

        // What we gave and what we took, one under the other so the two are read as
        // a pair — the same shape the sold list uses, narrowed to this one sale.
        Column(Modifier.weight(1f)) {
            Text(item?.name ?: "—", fontWeight = FontWeight.Medium)
            if (group.pieces > 1) {
                Text(
                    // Above one it is pieces, so it is counted in sztuki — and the
                    // bare count avoids a verb that would have to agree with it as
                    // well ("poszły 3 sztuki", but "poszło 12 sztuk").
                    sztuki(group.pieces),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = sellCostLabel(cost, group.pieces),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Sprzedaliśmy ${byThePiece(group.proceeds, group.pieces)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(12.dp))

        SellProfit(profit = profit, cost = cost)
    }
}

/**
 * What this one sale made, kept out of the pair it is drawn from — a loss said as a
 * loss rather than written as a negative gain. A sale with no cost behind it is set
 * against nothing, so its whole price is what it made; the line above still says we do
 * not know what it had cost.
 */
@Composable
private fun SellProfit(profit: Money, cost: SellCost?) {
    val lost = profit.minor < 0
    val approx = if (cost?.isEstimated == true) "ok. " else ""
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = if (lost) {
                "$approx${Money(-profit.minor).format()}"
            } else {
                "$approx${profit.format()}"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (lost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = if (lost) "Straciliśmy" else "Zarobiliśmy",
            style = MaterialTheme.typography.bodySmall,
            color = if (lost) MaterialTheme.colorScheme.error else muted,
        )
    }
}

/**
 * What this row's pieces had cost, said the same way the sold list says it for a
 * whole thing — a share of a lot or of a box is a guess and says "ok.", and a thing
 * with no buy behind it says we do not know.
 */
private fun sellCostLabel(cost: SellCost?, pieces: Int): String {
    if (cost == null) return "Nie wiemy, za ile kupiliśmy"
    return "Kupiliśmy ${byThePiece(cost.cost, pieces, approx = cost.isEstimated)}"
}

/**
 * The rest of a "Kupiliśmy …" or "Sprzedaliśmy …" line: per piece where there are
 * several — "po 15,00 zł za sztukę", the way the magazyn says a lot's cost — since
 * one ring's price is what was agreed ten times over. A total that will not divide
 * into whole grosze is said as the total instead, rather than rounded into a price
 * nobody ever paid.
 */
internal fun byThePiece(total: Money, pieces: Int, approx: Boolean = false): String {
    val ok = if (approx) "ok. " else ""
    return if (pieces > 1 && total.minor % pieces == 0L) {
        "po $ok${(total / pieces).format()} za sztukę"
    } else {
        "za $ok${total.format()}"
    }
}

/**
 * A row opens the thing, and which screen that is depends on where the thing is now:
 * in stock it is still ours to price, sell and delete; sold it is a record with four
 * numbers left to correct. A deleted one, and a `REMOVED` one written before removing
 * became a delete, belong to neither and open nothing.
 *
 * [sellingToday] travels with the item id rather than being decided over there: it is
 * a fact about the day this row was opened from, and only this screen knows which day
 * that is.
 */
private fun openItemOrNull(
    item: Item?,
    sellingToday: Boolean,
    onOpenStockItem: (itemId: String, selling: Boolean) -> Unit,
    onOpenSoldItem: (String) -> Unit,
): (() -> Unit)? = when (item?.status) {
    ItemStatus.IN_STOCK -> ({ onOpenStockItem(item.id, sellingToday) })
    ItemStatus.SOLD -> ({ onOpenSoldItem(item.id) })
    else -> null
}
