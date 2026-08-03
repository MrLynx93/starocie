package pl.starocie.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import pl.starocie.domain.LedgerRepository
import pl.starocie.domain.format
import pl.starocie.domain.sum

@Composable
fun HomeScreen(onBuy: () -> Unit, onSell: () -> Unit) {
    val repository: LedgerRepository = koinInject()
    val ledger by repository.ledger.collectAsState()
    val scope = rememberCoroutineScope()

    var renaming by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf("") }

    val today = ledger.events.maxByOrNull { it.date }
    val stock = ledger.itemsInStock()
    val stockValue = stock.mapNotNull { it.price }.sum()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {

        if (today != null) {
            val stats = ledger.eventStats(today)
            Text(
                text = today.name ?: today.date.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clickable {
                    draftName = today.name.orEmpty()
                    renaming = true
                },
            )
            Text(
                text = "wydane ${stats.spent.format()} · zarobione ${stats.earned.format()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigAction("Kupuję", Modifier.weight(1f), onBuy)
            BigAction("Sprzedaję", Modifier.weight(1f), onSell)
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "W magazynie: ${stock.size} · warte ${stockValue.format()}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "wartość wg cen wywoławczych",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        val recentSells = ledger.sells.sortedByDescending { it.createdAt }.take(20)
        if (recentSells.isEmpty()) {
            Text(
                "Nic jeszcze nie sprzedano.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(recentSells, key = { it.id }) { sell ->
                    val item = ledger.itemById(sell.itemId)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item?.name ?: "—")
                            val stats = item?.let { ledger.itemStats(it) }
                            val profit = stats?.profit
                            Text(
                                text = when {
                                    profit == null -> "zysk nieznany"
                                    stats.profitIsEstimated -> "zysk ok. ${profit.format()}"
                                    else -> "zysk ${profit.format()}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(sell.price.format(), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    if (renaming && today != null) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Nazwa dnia") },
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
            dismissButton = {
                TextButton(onClick = { renaming = false }) { Text("Anuluj") }
            },
        )
    }
}

@Composable
private fun BigAction(label: String, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(96.dp),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text(label, fontSize = 20.sp)
    }
}
