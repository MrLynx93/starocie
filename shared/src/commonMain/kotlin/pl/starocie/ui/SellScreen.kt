package pl.starocie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pl.starocie.domain.Item
import pl.starocie.domain.format

@Composable
fun SellScreen(onDone: () -> Unit) {
    val viewModel: SellViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            singleLine = true,
            placeholder = { Text("Szukaj albo wybierz poniżej") },
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.canCreateFromQuery) {
            TextButton(onClick = viewModel::createFromQuery) {
                Text("Dodaj \"${state.query}\" i sprzedaj")
            }
            Text(
                "Bez zakupu — koszt zostanie nieznany.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        if (state.inStock.isEmpty()) {
            Text(
                "Nic w magazynie.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.inStock, key = { it.id }) { item ->
                    StockTile(item, onClick = { viewModel.select(item) })
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Gotowe") }
    }

    state.selected?.let { item ->
        SellDialog(
            item = item,
            state = state,
            onPriceChange = viewModel::onPriceChange,
            onNoteChange = viewModel::onNoteChange,
            onSoldCompletelyChange = viewModel::onSoldCompletelyChange,
            onConfirm = viewModel::confirm,
            onRemove = { viewModel.remove(item) },
            onDismiss = viewModel::dismiss,
        )
    }
}

/**
 * The photo will be the identity here once the camera exists; until then the name,
 * or a visible "bez nazwy" so a nameless item is still recognisably present.
 */
@Composable
private fun StockTile(item: Item, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = item.name ?: "bez nazwy",
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
                item.price?.let {
                    Text(
                        "wyw. ${it.format()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.splittable) {
                    Text(
                        "na sztuki",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SellDialog(
    item: Item,
    state: SellUiState,
    onPriceChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSoldCompletelyChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name ?: "bez nazwy") },
        text = {
            Column {
                OutlinedTextField(
                    value = state.priceText,
                    onValueChange = onPriceChange,
                    singleLine = true,
                    label = { Text("Cena końcowa") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (item.splittable) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = onNoteChange,
                        singleLine = true,
                        label = { Text("Co poszło (opcjonalnie)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = state.soldCompletely,
                            onCheckedChange = onSoldCompletelyChange,
                        )
                        Text("Sprzedane w całości")
                    }
                }

                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onRemove) { Text("Usuń z magazynu") }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = state.canConfirm) { Text("Sprzedane") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
