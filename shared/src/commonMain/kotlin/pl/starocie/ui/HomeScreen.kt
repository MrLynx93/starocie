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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import pl.starocie.domain.ItemStatus
import pl.starocie.domain.LedgerRepository
import pl.starocie.domain.Money
import pl.starocie.domain.format
import pl.starocie.domain.sum

@Composable
fun HomeScreen(
    onBuyOne: () -> Unit,
    onBuyBox: () -> Unit,
    onSell: () -> Unit,
    onStock: () -> Unit,
    onSold: () -> Unit,
    onSessions: () -> Unit,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
) {
    val repository: LedgerRepository = koinInject()
    val ledger by repository.ledger.collectAsState()
    val syncError by repository.syncError.collectAsState()

    val today = ledger.events.maxByOrNull { it.date }
    val stock = ledger.itemsInStock()
    val stockValue = stock.mapNotNull { it.price }.sum()
    val sold = ledger.items.filter { it.status == ItemStatus.SOLD }
    val soldProceeds = sold.map { ledger.itemStats(it).proceeds }.sum()
    val recentSells = ledger.sells.sortedByDescending { it.createdAt }.take(30)
    // Every giełda's takings, which is the same sum as every sale's — an event is
    // the sole grouping, so nothing can fall outside one.
    val sessionsEarned = ledger.sells.map { it.price }.sum()

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

            Spacer(Modifier.height(12.dp))

            // The day is a caption under the title now, not a heading of its own —
            // it says which day the figures below belong to and nothing more.
            Text(
                text = today?.name ?: today?.date?.toString() ?: "jeszcze nic dziś nie robiliśmy",
                style = MaterialTheme.typography.titleSmall,
            )

            today?.let {
                val stats = ledger.eventStats(it)
                Text(
                    "Wydaliśmy ${stats.spent.format()} · Zarobiliśmy ${stats.earned.format()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            SummaryCard(
                title = "Mamy ${przedmioty(stock.size)}",
                subtitle = "chcemy za nie ${stockValue.format()}",
                openLabel = "Pokaż magazyn",
                onClick = onStock,
            )

            Spacer(Modifier.height(10.dp))

            SummaryCard(
                title = "Sprzedaliśmy ${przedmioty(sold.size)}",
                subtitle = "wzięliśmy za nie ${soldProceeds.format()}",
                openLabel = "Pokaż, co sprzedaliśmy",
                onClick = onSold,
            )

            Spacer(Modifier.height(10.dp))

            // The third card is about days rather than things: how many giełd we have
            // been to and what they brought in altogether.
            SummaryCard(
                title = "Mamy za sobą ${giełdy(ledger.events.size)}",
                subtitle = if (ledger.events.isEmpty()) {
                    "jeszcze nigdzie nie byliśmy"
                } else {
                    "wzięliśmy na nich ${sessionsEarned.format()}"
                },
                openLabel = "Pokaż nasze giełdy",
                onClick = onSessions,
            )

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
                        val profit = stats?.profit
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
                                        text = when {
                                            profit == null -> "nie wiemy, ile zarobiliśmy"
                                            profit.minor < 0 ->
                                                "straciliśmy $approx${Money(-profit.minor).format()}"
                                            else -> "zarobiliśmy $approx${profit.format()}"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (profit != null && profit.minor < 0) {
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
