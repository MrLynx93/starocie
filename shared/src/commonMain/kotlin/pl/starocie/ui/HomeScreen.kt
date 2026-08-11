package pl.starocie.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
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
import pl.starocie.domain.ItemStatus
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
    onTodaySession: (String) -> Unit,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
) {
    val repository: LedgerRepository = koinInject()
    val ledger by repository.ledger.collectAsState()
    val syncError by repository.syncError.collectAsState()

    val stock = ledger.itemsInStock()
    val stockValue = stock.mapNotNull { it.price }.sum()
    val sold = ledger.items.filter { it.status == ItemStatus.SOLD }
    val soldProceeds = sold.map { ledger.itemStats(it).proceeds }.sum()
    val recentSells = ledger.sells.sortedByDescending { it.createdAt }.take(30)
    // What every giełda made, not what it took: each sale against what its own
    // pieces cost. Takings minus spending would be two unrelated days' money.
    val sessions = remember(ledger) { ledger.overallStats() }
    // A day we only bought on is not a giełda, so it is not counted as one — and the
    // list behind this card leaves out exactly the same days.
    val sessionCount = remember(ledger) { ledger.sellingSessions().size }

    // The giełda we are standing at, and only while we are standing at it: the day
    // every write resolves to, once something has actually gone at it. The same rule
    // the list and its card count by — a day we have only bought on is not a giełda,
    // so there is nothing here to open yet. The clock is read again on every write,
    // which is what carries the card off the screen at midnight.
    val today = remember(ledger) {
        val id = CurrentEventResolver().eventIdFor(Clock.System.now())
        ledger.eventById(id)
            ?.takeIf { ledger.sellsOfEvent(it.id).isNotEmpty() }
            ?.let { it to ledger.eventStats(it) }
    }

    Scaffold(
        floatingActionButton = {
            // IntrinsicSize.Max sizes the column to its widest child, so all three
            // buttons match without a hard-coded width that would break if a label
            // changed.
            Column(
                modifier = Modifier.width(IntrinsicSize.Max),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HomeAction(
                    label = "Kup paczkę",
                    icon = Icons.Filled.Inventory2,
                    onClick = onBuyBox,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(2.dp),
                )
                HomeAction(
                    label = "Kup",
                    icon = Icons.Filled.AddShoppingCart,
                    onClick = onBuyOne,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(2.dp),
                )
                HomeAction(
                    label = "Sprzedaj",
                    icon = Icons.Filled.Sell,
                    onClick = onSell,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
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

            SummaryCard(
                title = "Mamy ${przedmioty(stock.size)}",
                subtitle = "Chcemy sprzedać za łącznie ${stockValue.format()}",
                openLabel = "Pokaż magazyn",
                onClick = onStock,
            )

            Spacer(Modifier.height(10.dp))

            SummaryCard(
                title = "Sprzedaliśmy ${przedmioty(sold.size)}",
                subtitle = "Sprzedaliśmy za łącznie ${soldProceeds.format()}",
                openLabel = "Pokaż, co sprzedaliśmy",
                onClick = onSold,
            )

            Spacer(Modifier.height(10.dp))

            // The third card is about days rather than things: how many giełd we have
            // been to and what we made on them altogether.
            SummaryCard(
                title = "Mamy za sobą ${giełdy(sessionCount)}",
                subtitle = when {
                    sessionCount == 0 -> "Jeszcze nigdzie nie byliśmy"
                    else -> sessionsProfitLine(sessions)
                },
                openLabel = "Pokaż nasze giełdy",
                onClick = onSessions,
            )

            // Today's, under all of them — the day being had rather than the days we
            // have had. It says what has gone and what that took, and nothing about
            // profit: a giełda in progress is a stall being worked, and what a day
            // made is a question for the day itself, on the screen behind this card.
            today?.let { (event, stats) ->
                Spacer(Modifier.height(10.dp))

                SummaryCard(
                    title = "Dzisiejsza giełda",
                    subtitle = "Sprzedaliśmy ${przedmioty(stats.itemsSold)} za ${stats.earned.format()}",
                    openLabel = "Pokaż dzisiejszą giełdę",
                    onClick = { onTodaySession(event.id) },
                )
            }

            Spacer(Modifier.height(20.dp))

            if (recentSells.isEmpty()) {
                Text(
                    "Jeszcze nic nie sprzedaliśmy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Co ostatnio sprzedaliśmy", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recentSells, key = { it.id }) { sell ->
                        val item = ledger.itemById(sell.itemId)
                        val stats = item?.let { ledger.itemStats(it) }
                        // A deleted thing takes its cost with it, so what the sale
                        // took is the whole of what it made — the same answer an
                        // item we never recorded buying gives.
                        val profit = stats?.profit ?: sell.price
                        val approx = if (stats?.profitIsEstimated == true) "ok. " else ""

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
        }
    }

}

/**
 * One of the round buttons.
 *
 * The label carries the weight rather than the icon, so both start at the same left
 * edge down the stack: three centred labels of different lengths read as three
 * unrelated buttons, and the eye has to find each one afresh.
 */
@Composable
private fun HomeAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        // The weight is what pushes the pair left: the row would otherwise centre
        // them in a button that is as wide as the widest label.
        Text(label, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
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

/** A read-out with a list behind it. */
@Composable
private fun SummaryCard(
    title: String,
    subtitle: String,
    openLabel: String,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The card was a read-out for long enough that nothing about it
            // suggests it opens anything; the chevron is the only cue that the
            // list is behind it.
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = openLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
