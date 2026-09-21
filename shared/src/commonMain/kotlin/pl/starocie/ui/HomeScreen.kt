package pl.starocie.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.koin.compose.koinInject
import pl.starocie.domain.CurrentEventResolver
import pl.starocie.domain.EventStats
import pl.starocie.domain.LedgerRepository
import pl.starocie.domain.Money
import pl.starocie.domain.format
import pl.starocie.domain.sum

@OptIn(ExperimentalTime::class)
@Composable
fun HomeScreen(
    onBuyOne: () -> Unit,
    onBuyBox: () -> Unit,
    onSell: () -> Unit,
    onStock: () -> Unit,
    onSold: () -> Unit,
    onSessions: () -> Unit,
    onBuyingSessions: () -> Unit,
    onTodaySession: (String) -> Unit,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
) {
    val repository: LedgerRepository = koinInject()
    val ledger by repository.ledger.collectAsState()
    val syncError by repository.syncError.collectAsState()
    val loading by repository.loading.collectAsState()
    // An empty ledger reads the same whether it is still arriving or genuinely
    // empty, which is what the repository's own flag is for. The buttons are not
    // in this: a write never waits for the network, so buying and selling work
    // before a single document has landed.
    val skeleton = rememberSkeletonVisible(loading)

    val stock = ledger.itemsInStock()
    val stockValue = stock.mapNotNull { it.price }.sum()
    val recentSells = ledger.sells.sortedByDescending { it.createdAt }.take(30)
    // What every giełda made, not what it took: each sale against what its own
    // pieces cost. Takings minus spending would be two unrelated days' money. It is
    // also what the second card counts and totals by — see there.
    val sessions = remember(ledger) { ledger.overallStats() }
    // A day we only bought on is not a giełda, so it is not counted as one — and the
    // list behind this card leaves out exactly the same days.
    val sessionCount = remember(ledger) { ledger.sellingSessions().size }

    // Those days are not nothing, though: a trip to somebody's garage is where a
    // whole afternoon's spending went, and until now the only trace of it was the
    // things themselves in the magazyn. They get a card of their own, counted and
    // totted up the way the giełdy are — and the two cannot overlap, a day being one
    // or the other by construction.
    val buyingDays = remember(ledger) {
        ledger.buyingSessions().map { ledger.eventStats(it) }
    }
    val buyingSpent = remember(buyingDays) { buyingDays.map { it.spent }.sum() }

    // The day we are standing in, and only while we are standing in it: the one every
    // write resolves to, once anything at all has happened at it. Which kind of day
    // it is follows the same rule the two cards above count by — sold something and
    // it is a giełda, only bought and it is a day's shopping — so the card says
    // whichever it currently is and changes to the other the moment the first thing
    // goes. The clock is read again on every write, which is what carries the card
    // off the screen at midnight.
    val today = remember(ledger) {
        val id = CurrentEventResolver().eventIdFor(Clock.System.now())
        ledger.eventById(id)
            ?.let { it to ledger.eventStats(it) }
            ?.takeIf { (_, stats) -> stats.sellCount > 0 || stats.buyCount > 0 }
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // **The screen scrolls as one.** It used to not scroll at all: only the
            // recent-sales list at the bottom did, so the cards above could never move
            // out of the way and the list got whatever height was left over — which on
            // a smaller phone, or on a day when both of the optional cards are showing,
            // is close to none of it. The list is a plain column inside this scroll
            // rather than a lazy one, because a lazy list inside a scrolling column has
            // no height to be measured against and crashes; thirty small cards compose
            // in no time, and the whole ledger is in memory anyway.
            Column(
                modifier = Modifier.weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                syncError?.let {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Nie synchronizujemy się: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // The switch rides on the title line rather than in a top bar: it is the
                // only app-wide setting there is, and a whole bar to hold one button
                // would cost every screen the height it earns nothing with.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Nasze starocie",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onToggleTheme) {
                        // The icon is what the tap *gives you*, not what you are in:
                        // a sun to go bright, a moon to go dark.
                        Icon(
                            if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = if (isDark) "Rozjaśnij" else "Przyciemnij",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // **The day we are standing in comes first**, where it used to come
                // last, under four totals that do not change from one hour to the next.
                // At a stall it is the card you opened the app for, and the four above
                // it were between it and the thumb. It is in the secondary colour for
                // the same reason: the others are what we have done, this is what is
                // happening, and they should not have to be told apart by reading them.
                //
                // Only while we are standing in it: the day every write resolves to,
                // once anything at all has happened at it. Which kind of day it is
                // follows the same rule the two totals count by — sold something and it
                // is a giełda, only bought and it is a day's shopping — so it says
                // whichever it currently is and turns into the other the moment the
                // first thing goes. It carries no profit: a giełda in progress is a
                // stall being worked, and what a day made is a question for the day
                // itself, on the screen behind the card.
                today?.let { (event, stats) ->
                    val selling = stats.sellCount > 0

                    SummaryCard(
                        // The calendar rather than the stall below it: that one and
                        // this one are the same kind of thing, and what separates them
                        // is that this one is about now.
                        icon = Icons.Filled.Today,
                        title = if (selling) "Dzisiejsza giełda" else "Dzisiejsze zakupy",
                        subtitle = if (selling) {
                            "Sprzedaliśmy ${przedmioty(stats.itemsSold)} za ${stats.earned.format()}"
                        } else {
                            "Kupiliśmy ${przedmioty(stats.itemsBought)} za ${stats.spent.format()}"
                        },
                        openLabel = if (selling) "Pokaż dzisiejszą giełdę" else "Pokaż dzisiejsze zakupy",
                        onClick = { onTodaySession(event.id) },
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                    )

                    Spacer(Modifier.height(12.dp))
                }

                // **The four totals are one card with four rows, not four cards.**
                // They were four of the same shape in the same colour with the same
                // chevron, 320 dp of them, and because they looked alike the eye had to
                // read all four to find one. They are one thing with four lines in it
                // now — which is also what they are: everything we have, added up. Each
                // row still opens its own list, and every sentence is intact, the point
                // of this being the four boxes rather than the words in them.
                TotalsCard {
                    TotalRow(
                        // A warehouse, and not the box on "Kup paczkę": one icon
                        // meaning two things on one screen is worse than either of
                        // them being perfect.
                        icon = Icons.Filled.Warehouse,
                        title = if (skeleton) null else "Mamy ${przedmioty(stock.size)}",
                        subtitle = if (skeleton) null else "Chcemy sprzedać za łącznie ${stockValue.format()}",
                        openLabel = "Pokaż magazyn",
                        onClick = onStock,
                        titleWidth = 0.54f,
                        subtitleWidth = 0.80f,
                        divider = false,
                    )

                    TotalRow(
                        // Money taken, rather than the tag on "Sprzedaj": the button is
                        // the act and this is what the act came to.
                        icon = Icons.Filled.Payments,
                        // Pieces, out of the days themselves, and not the things that
                        // are wholly gone: a lot of twelve plates that went at one
                        // giełda is twelve things sold there, and counting the record
                        // instead made it one — so the home screen answered smaller
                        // than the giełdy it is the total of. A lot sold in part is the
                        // other half of the same wrongness: it is still IN_STOCK, so
                        // what went out of it counted nowhere here at all, money
                        // included. Both are the same figure the day's own row reads,
                        // summed over every day.
                        title = if (skeleton) null else "Sprzedaliśmy ${przedmioty(sessions.itemsSold)}",
                        subtitle = if (skeleton) null else "Sprzedaliśmy za łącznie ${sessions.earned.format()}",
                        openLabel = "Pokaż, co sprzedaliśmy",
                        onClick = onSold,
                        titleWidth = 0.62f,
                        subtitleWidth = 0.74f,
                    )

                    // About days rather than things: how many giełd we have been to and
                    // what we made on them altogether.
                    TotalRow(
                        // A stall face-on, which is the app's own mark: this row is the
                        // door to the giełdy themselves.
                        icon = Icons.Filled.Storefront,
                        title = if (skeleton) null else "Mamy za sobą ${giełdy(sessionCount)}",
                        subtitle = when {
                            skeleton -> null
                            sessionCount == 0 -> "Jeszcze nigdzie nie byliśmy"
                            else -> sessionsProfitLine(sessions)
                        },
                        openLabel = "Pokaż nasze giełdy",
                        onClick = onSessions,
                        titleWidth = 0.50f,
                        subtitleWidth = 0.68f,
                    )

                    // The days we only shopped on, under the giełdy and counted the same
                    // way: both rows are a number of days and what those days came to.
                    // Only ever drawn when there has been one — a row saying we have
                    // never had a day of only buying is a line about nothing.
                    if (buyingDays.isNotEmpty()) {
                        TotalRow(
                            // The basket rather than the stall: this is the other half
                            // of what a day can be, and what we did on these days is
                            // carry things home.
                            icon = Icons.Filled.ShoppingBasket,
                            title = "Mamy za sobą ${dniZakupów(buyingDays.size)}",
                            // What they cost, and nothing about profit: nothing was sold
                            // on any of them, so there is no profit to have an opinion
                            // about — what came home from them is in the magazyn, at the
                            // row above.
                            subtitle = "Kupiliśmy na nich za łącznie ${buyingSpent.format()}",
                            openLabel = "Pokaż dni zakupów",
                            onClick = onBuyingSessions,
                            titleWidth = 0.56f,
                            subtitleWidth = 0.72f,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                if (skeleton) {
                    // The heading is a skeleton too: whether there is anything to head
                    // is one of the things we do not know yet, and a heading that
                    // arrives certain over rows that are still guesses is a small lie.
                    SkeletonLine(fraction = 0.42f, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Varying widths, so three rows read as three things rather
                        // than as a form waiting to be filled in.
                        listOf(0.55f to 0.72f, 0.44f to 0.80f, 0.61f to 0.66f).forEach {
                            SkeletonSellRow(nameWidth = it.first, profitWidth = it.second)
                        }
                    }
                } else if (recentSells.isEmpty()) {
                    Text(
                        "Jeszcze nic nie sprzedaliśmy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Co ostatnio sprzedaliśmy", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recentSells.forEach { sell ->
                            val item = ledger.itemById(sell.itemId)
                            // This sale against what its own pieces cost, as a giełda's
                            // rows have it — not the thing's whole profit, which set
                            // three pieces of a lot against what all of them cost. A
                            // deleted thing, or one never recorded as bought, has no
                            // cost to set it against, so what the sale took is what it
                            // made.
                            val cost = ledger.sellCost(sell)
                            val profit = ledger.sellProfit(sell)
                            val approx = if (cost?.isEstimated == true) "ok. " else ""

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                                elevation = CardDefaults.cardElevation(1.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item?.name ?: "—", fontWeight = FontWeight.Medium)
                                        Text(
                                            // A loss written as a negative gain is a
                                            // small puzzle every time; said as a loss it
                                            // is just what happened.
                                            text = if (profit.minor < 0) {
                                                "Straciliśmy $approx${Money(-profit.minor).format()}"
                                            } else {
                                                "Zarobiliśmy $approx${profit.format()}"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (profit.minor < 0) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                    Text(
                                        sell.price.format(),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
            }

            HomeActions(onBuyBox = onBuyBox, onBuyOne = onBuyOne, onSell = onSell)
        }
    }
}

/**
 * The three things this screen is for, pinned under the scroll.
 *
 * They were three extended FABs stacked in the bottom-right corner — 188 dp of them,
 * floating over the recent-sales list, which is the one part of this screen that is
 * not a read-out. Nothing scrolled out from under them either, the screen not having
 * scrolled at all. Pinned instead of floating, they cover nothing and are always
 * there, and the list gets those 188 dp back.
 *
 * **"Sprzedaj" keeps a row of its own** and the two buys share one above it: at a
 * stall this screen exists to start a sale, and the thumb should not have to pick
 * that button out of three of the same size. That is also what lets the pair shrink —
 * side by side they need neither the full height nor the left-aligned labels the
 * stack needed to read as one stack.
 */
@Composable
private fun HomeActions(onBuyBox: () -> Unit, onBuyOne: () -> Unit, onSell: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BuyAction("Kup paczkę", Icons.Filled.Inventory2, onBuyBox, Modifier.weight(1f))
            BuyAction("Kup", Icons.Filled.AddShoppingCart, onBuyOne, Modifier.weight(1f))
        }

        Button(
            onClick = onSell,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Filled.Sell, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Sprzedaj", fontWeight = FontWeight.SemiBold)
        }
    }
}

/** One of the two buys: half a row each, in the secondary colour that says so. */
@Composable
private fun BuyAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = modifier.height(52.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        // One line always: "Kup paczkę" has about 115 dp to live in at half a
        // phone's width, and wrapping it would make one button taller than its pair.
        Text(label, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/**
 * Everything we have, added up: one card, one row per question.
 *
 * Four cards of the same shape, the same colour and the same chevron is four things
 * to read before finding one, and they are not four things — they are one list of
 * totals. One container says that, and it costs three hairlines instead of three
 * 10 dp gaps and one padding instead of four.
 *
 * The rows themselves are unchanged: the whole sentence, both lines of it, because
 * what was crowding this screen was the boxes and not the words.
 */
@Composable
private fun TotalsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp), content = content)
    }
}

/**
 * One line of that card, with its list behind it.
 *
 * A null [title] or [subtitle] is a figure we do not have yet and stands in as a bar,
 * exactly as it did when these were cards: only the read-out goes, the row keeps its
 * [icon] and its chevron and stays openable, and nothing moves when the figures land.
 *
 * [divider] is the hairline *above* the row rather than below it, so the first row
 * turns it off and the last one — which is conditional — needs to know nothing about
 * whether it is last.
 */
@Composable
private fun TotalRow(
    icon: ImageVector,
    title: String?,
    subtitle: String?,
    openLabel: String,
    onClick: () -> Unit,
    titleWidth: Float = 0.55f,
    subtitleWidth: Float = 0.75f,
    divider: Boolean = true,
) {
    if (divider) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tinted like the chevron rather than like the title: a row is a read-out with
        // a way in at each end, and a full-strength icon pulls the eye off the figure
        // the row is for.
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            if (title == null) {
                SkeletonLine(titleWidth, MaterialTheme.typography.titleSmall)
            } else {
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            if (subtitle == null) {
                SkeletonLine(subtitleWidth, MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = openLabel,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * What every giełda made together, in the one line a card has.
 *
 * Only ever read when there is a giełda to read it about, and a giełda is a day we
 * sold something on — so there is no line here for having sold nothing. The card
 * says "Jeszcze nigdzie nie byliśmy" instead, which is the truth in that case: a
 * day of only buying never was a market.
 */
private fun sessionsProfitLine(stats: EventStats): String {
    val approx = if (stats.profitIsEstimated) "ok. " else ""
    return if (stats.profit.minor < 0) {
        "Straciliśmy na nich $approx${Money(-stats.profit.minor).format()}"
    } else {
        "Zarobiliśmy na nich $approx${stats.profit.format()}"
    }
}

/**
 * One row of recent activity, before we know what was sold.
 *
 * It carries the real row's card, padding and line heights rather than a
 * rectangle of its own, so the list does not resize under the thumb when the sales
 * arrive.
 */
@Composable
private fun SkeletonSellRow(nameWidth: Float, profitWidth: Float) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                SkeletonLine(nameWidth, MaterialTheme.typography.bodyLarge)
                SkeletonLine(profitWidth, MaterialTheme.typography.bodySmall)
            }
            SkeletonLine(
                fraction = 1f,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(64.dp),
            )
        }
    }
}

/**
 * A read-out with a list behind it, and the day being had is the only one left.
 *
 * The four totals under it are rows of one card now ([TotalsCard]); this stayed a
 * card of its own because it is not one of them — they are what we have done and
 * this is what is happening — and [container] is how that is said without having to
 * be read. It is the secondary colour, which on this screen means "now".
 *
 * A null [title] or [subtitle] is a figure we do not have yet and stands in as a
 * bar. Only the read-out goes: the card keeps its shape, its colour, its [icon] and
 * its chevron, and stays openable — the list behind it is reachable whether or not
 * we can say what is in it, and a card that changed shape on arrival would move
 * everything below it. Which card this is was never something we were waiting on,
 * so the icon is drawn for real while the figures are still bars.
 */
@Composable
private fun SummaryCard(
    icon: ImageVector,
    title: String?,
    subtitle: String?,
    openLabel: String,
    onClick: () -> Unit,
    titleWidth: Float = 0.55f,
    subtitleWidth: Float = 0.75f,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    onContainer: Color = MaterialTheme.colorScheme.onSurface,
) {
    // Whatever sits on the container, at reading strength for the title and softened
    // for the line under it and for the two icons — the same three weights the card
    // had when it was on surfaceVariant, so the colour is the only thing that changed.
    val soft = onContainer.copy(alpha = 0.76f)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tinted like the chevron rather than like the title: a card is a
            // read-out with a way in at each end, and a full-strength icon pulls the
            // eye off the figure the card is for.
            Icon(icon, contentDescription = null, tint = soft)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                if (title == null) {
                    SkeletonLine(titleWidth, MaterialTheme.typography.titleMedium)
                } else {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = onContainer)
                }
                if (subtitle == null) {
                    SkeletonLine(subtitleWidth, MaterialTheme.typography.bodySmall)
                } else {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = soft)
                }
            }
            // The card was a read-out for long enough that nothing about it
            // suggests it opens anything; the chevron is the only cue that the
            // list is behind it.
            Icon(Icons.Filled.ChevronRight, contentDescription = openLabel, tint = soft)
        }
    }
}
