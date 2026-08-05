package pl.starocie.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SellScreen(onDone: () -> Unit, onAddNew: () -> Unit) {
    val viewModel: SellViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            singleLine = true,
            placeholder = { Text("Co sprzedajemy?") },
            modifier = Modifier.fillMaxWidth(),
        )

        // The thing may never have been recorded — most of the time, at the start,
        // it has not been. Adding it here is the same motion as buying it.
        if (state.canAddNew) {
            TextButton(onClick = { viewModel.startNewItem(); onAddNew() }) {
                Text(
                    if (state.query.isBlank()) {
                        "Dodaj nowy przedmiot i sprzedaj"
                    } else {
                        "Dodaj \"${state.query}\" i sprzedaj"
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.inStock.isEmpty()) {
            Text(
                if (state.query.isBlank()) {
                    "Nic tu jeszcze nie mamy."
                } else {
                    "Nic takiego nie mamy."
                },
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
        BackButton(onDone)
    }

    state.selected?.let { item ->
        SellDialog(
            item = item,
            state = state,
            onPriceChange = viewModel::onPriceChange,
            onNoteChange = viewModel::onNoteChange,
            onSoldCompletelyChange = viewModel::onSoldCompletelyChange,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismiss,
        )
    }
}
