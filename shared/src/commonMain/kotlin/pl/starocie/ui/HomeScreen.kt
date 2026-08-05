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
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
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
) {
    val repository: LedgerRepository = koinInject()
    val ledger by repository.ledger.collectAsState()
    val syncError by repository.syncError.collectAsState()
    val scope = rememberCoroutineScope()

    var renaming by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf("") }

    val today = ledger.events.maxByOrNull { it.date }
    val stock = ledger.itemsInStock()
    val stockValue = stock.mapNotNull { it.price }.sum()
    val recentSells = ledger.sells.sortedByDescending { it.createdAt }.take(30)

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
                ExtendedFloatingActionButton(
                    text = { Text("Kup paczkę") },
                    icon = { Icon(Icons.Filled.Inventory2, contentDescription = null) },
                    onClick = onBuyBox,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(2.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                ExtendedFloatingActionButton(
                    text = { Text("Kup") },
                    icon = { Icon(Icons.Filled.AddShoppingCart, contentDescription = null) },
                    onClick = onBuyOne,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(2.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                ExtendedFloatingActionButton(
                    text = { Text("Sprzedaj", fontWeight = FontWeight.Medium) },
                    icon = { Icon(Icons.Filled.Sell, contentDescription = null) },
                    onClick = onSell,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.fillMaxWidth(),
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

            Text(
                text = today?.name ?: today?.date?.toString() ?: "starocie",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.clickable(enabled = today != null) {
                    draftName = today?.name.orEmpty()
                    renaming = true
                },
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

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth().clickable(onClick = onStock),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Mamy ${przedmioty(stock.size)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "chcemy za nie ${stockValue.format()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // The card was a read-out for long enough that nothing about it
                    // suggests it opens anything; the chevron is the only cue that
                    // the list is behind it.
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Pokaż magazyn",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

    if (renaming && today != null) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Jak nazwiemy ten dzień?") },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    singleLine = true,
                    placeholder = { Text("np. Hala Mirowska") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.nameEvent(today.id, draftName) }
                    renaming = false
                }) { Text("Zapisz") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Anuluj") } },
        )
    }
}
