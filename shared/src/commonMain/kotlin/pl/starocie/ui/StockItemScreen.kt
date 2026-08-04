package pl.starocie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import pl.starocie.domain.ItemStatus
import pl.starocie.domain.LedgerRepository
import pl.starocie.domain.format

/**
 * One thing in stock, in full, with the two things you can do to it.
 *
 * Removing used to sit inside the sell dialog, one thumb-width from the price
 * field — a screen you reach by searching for something to sell is the wrong place
 * to take it out of stock instead. Here there is room to read what a thing cost
 * before deciding, and the button that resolves it without proceeds is a
 * deliberate stop rather than a near miss.
 *
 * It leaves by itself the moment the item stops being in stock, so a completed
 * sale or a removal lands you back in the list it came from. A lot sold in part is
 * still in stock, so the screen stays and simply shows one more sale against it.
 */
@Composable
fun StockItemScreen(itemId: String, onDone: () -> Unit) {
    val repository: LedgerRepository = koinInject()
    val ledger by repository.ledger.collectAsState()
    val viewModel: SellViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val item = ledger.itemById(itemId)
    var confirmingRemoval by remember { mutableStateOf(false) }

    // Waiting to have seen it in stock first: the ledger is empty for the instant
    // before the first snapshot arrives, and popping on that would close the screen
    // as it opens.
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(item?.status) {
        if (item != null && item.status == ItemStatus.IN_STOCK) seen = true else if (seen) onDone()
    }

    if (item == null) return

    val stats = remember(ledger, item) { ledger.itemStats(item) }

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(item.name, style = MaterialTheme.typography.headlineSmall)

        if (item.splittable) {
            Text(
                "${item.quantity} szt. · sprzedaje się po kawałku",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(16.dp))

        PhotoView(item.photo, modifier = Modifier.height(220.dp))

        Spacer(Modifier.height(20.dp))

        Detail("Chcemy sprzedać za", item.price?.format() ?: "jeszcze nie wiemy")

        // An exact cost and a guess must never look alike, so the share of a box
        // says so both in the number and under it.
        Detail(
            label = "Kupiliśmy za",
            value = when {
                stats.cost == null -> "nie wiemy"
                stats.costIsEstimated -> "ok. ${stats.cost.format()}"
                else -> stats.cost.format()
            },
            hint = when {
                stats.cost == null -> "Nie zapisaliśmy zakupu, więc zysku nie policzymy."
                stats.costIsEstimated -> "To udział w cenie paczki, nie dokładna cena."
                else -> null
            },
        )

        if (stats.sellCount > 0) {
            Detail(
                label = "Sprzedaliśmy do tej pory",
                value = "${stats.sellCount} × · ${stats.proceeds.format()}",
                hint = "Zostaje w magazynie, aż zaznaczymy «sprzedane w całości».",
            )
        }

        item.note?.takeIf { it.isNotBlank() }?.let { Detail("Notatka", it) }

        Detail("Kupiliśmy dnia", item.date.toString())

        // A failed removal leaves the item in stock, so the screen stays put and
        // has to say why rather than looking like nothing was pressed.
        state.error?.let {
            Spacer(Modifier.height(16.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.select(item) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Sprzedaj", fontWeight = FontWeight.Medium) }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = { confirmingRemoval = true },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Usuń z magazynu") }

        Spacer(Modifier.height(10.dp))

        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Wróć") }
    }

    state.selected?.let { selected ->
        SellDialog(
            item = selected,
            state = state,
            onPriceChange = viewModel::onPriceChange,
            onNoteChange = viewModel::onNoteChange,
            onSoldCompletelyChange = viewModel::onSoldCompletelyChange,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismiss,
        )
    }

    if (confirmingRemoval) {
        AlertDialog(
            onDismissRequest = { confirmingRemoval = false },
            title = { Text("Wyjmujemy z magazynu?") },
            text = {
                Text(
                    "Zepsute, zgubione, oddane albo zostawiamy sobie — zniknie z " +
                        "magazynu bez sprzedaży, a to, co za nie zapłaciliśmy, " +
                        "policzymy jako stratę. Zapis zostaje, żeby liczby się zgadzały.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingRemoval = false
                    viewModel.remove(item)
                }) { Text("Usuń") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingRemoval = false }) { Text("Anuluj") }
            },
        )
    }
}

@Composable
private fun Detail(label: String, value: String, hint: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 16.dp)) {
            Text(value, style = MaterialTheme.typography.bodyLarge)
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    HorizontalDivider()
}
