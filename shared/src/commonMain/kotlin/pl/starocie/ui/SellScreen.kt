package pl.starocie.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
            placeholder = { Text("Zacznij pisać, co sprzedajesz") },
            modifier = Modifier.fillMaxWidth(),
        )

        // The thing may never have been recorded — most of the time, at the start,
        // it has not been. Adding it here is the same motion as buying it.
        if (state.canAddNew) {
            TextButton(onClick = viewModel::startNewItem) {
                Text(
                    if (state.query.isBlank()) {
                        "Dodaj nową rzecz i sprzedaj"
                    } else {
                        "Dodaj \"${state.query}\" i sprzedaj"
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.inStock.isEmpty()) {
            Text(
                if (state.query.isBlank()) "Nic w magazynie." else "Nic nie pasuje.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.inStock, key = { it.id }) { item ->
                    StockRow(item, onClick = { viewModel.select(item) })
                    HorizontalDivider()
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Gotowe") }
    }

    state.newItem?.let { form ->
        NewItemDialog(
            form = form,
            error = state.error,
            onChange = viewModel::onNewItemChange,
            onPhotoCaptured = viewModel::onNewPhotoCaptured,
            onClearPhoto = viewModel::clearNewPhoto,
            onConfirm = viewModel::confirmNewItem,
            onDismiss = viewModel::cancelNewItem,
        )
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

@Composable
private fun StockRow(item: Item, onClick: () -> Unit) {
    val thumb = remember(item.photo) { item.photo?.let { decodePhoto(it) } }

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // A photo helps you spot the thing among similarly named ones; the name
        // still does the finding, so a missing photo costs nothing.
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(12.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.Medium)
            if (item.splittable) {
                Text(
                    "${item.quantity} szt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        item.price?.let {
            Text(
                it.format(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Buying and selling in one sitting, for a thing that was never entered.
 *
 * Only the name and the final price are asked for. "Zapłacono" left empty is a
 * real answer — the cost is then unknown and stays unknown, which is the whole
 * point of tolerating a shortcut rather than demanding tidy books.
 */
@Composable
private fun NewItemDialog(
    form: NewItemForm,
    error: String?,
    onChange: (NewItemForm) -> Unit,
    onPhotoCaptured: (String?) -> Unit,
    onClearPhoto: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val takePhoto = rememberPhotoCapture(onPhotoCaptured)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nowa rzecz") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { onChange(form.copy(name = it)) },
                    singleLine = true,
                    label = { Text("Co to jest") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = form.paidText,
                        onValueChange = { onChange(form.copy(paidText = it)) },
                        singleLine = true,
                        label = { Text("Zapłacono") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = form.priceText,
                        onValueChange = { onChange(form.copy(priceText = it)) },
                        singleLine = true,
                        label = { Text("Cena końcowa") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = form.quantityText,
                        onValueChange = { onChange(form.copy(quantityText = it)) },
                        singleLine = true,
                        label = { Text("Szt.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.7f),
                    )
                }

                Text(
                    "Nie wiesz, za ile kupione? Zostaw puste — koszt będzie nieznany.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = form.note,
                    onValueChange = { onChange(form.copy(note = it)) },
                    singleLine = true,
                    label = { Text("Notatka (opcjonalnie)") },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (form.splittable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = form.soldCompletely,
                            onCheckedChange = { onChange(form.copy(soldCompletely = it)) },
                        )
                        Text("Sprzedane w całości")
                    }
                }

                Spacer(Modifier.height(8.dp))

                PhotoArea(
                    photo = form.photo,
                    onCapture = takePhoto,
                    onClear = onClearPhoto,
                    modifier = Modifier.height(160.dp),
                )

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = form.canConfirm) { Text("Sprzedane") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
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
        title = { Text(item.name) },
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

                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
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
