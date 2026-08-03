package pl.starocie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pl.starocie.domain.format

@Composable
fun BuyScreen(onDone: () -> Unit) {
    val viewModel: BuyViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    var itemName by remember { mutableStateOf("") }
    var itemPrice by remember { mutableStateOf("") }
    var itemSplittable by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {

        Text("Ile zapłacono", style = MaterialTheme.typography.titleMedium)
        Text(
            "Jedna liczba — tyle wyszło z portfela.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = state.totalText,
            onValueChange = viewModel::onTotalChange,
            singleLine = true,
            placeholder = { Text("0,00") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            singleLine = true,
            label = { Text("Skąd / co to (opcjonalnie)") },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Text("Co w tym było", style = MaterialTheme.typography.titleMedium)
        Text(
            "Jedna rzecz — koszt dokładny. Więcej — cena dzieli się wg cen wywoławczych.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = itemName,
                onValueChange = { itemName = it },
                singleLine = true,
                label = { Text("Nazwa") },
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = itemPrice,
                onValueChange = { itemPrice = it },
                singleLine = true,
                label = { Text("Cena wyw.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = itemSplittable, onCheckedChange = { itemSplittable = it })
            Text("Na sztuki", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    viewModel.addDraft(itemName, itemPrice, itemSplittable)
                    itemName = ""
                    itemPrice = ""
                    itemSplittable = false
                },
            ) { Text("Dodaj rzecz") }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(state.preview) { index, (draft, share) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(draft.name ?: "bez nazwy", fontWeight = FontWeight.Medium)
                            val asking = draft.price?.format()?.let { "wyw. $it" }
                            val split = if (draft.splittable) " · na sztuki" else ""
                            if (asking != null || split.isNotEmpty()) {
                                Text(
                                    (asking ?: "") + split,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (share != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(share.format(), fontWeight = FontWeight.Medium)
                                Text(
                                    if (state.previewIsEstimated) "koszt szacowany" else "koszt",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = { viewModel.removeDraft(index) }) { Text("×") }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Anuluj") }
            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.weight(2f),
            ) { Text("Zapisz zakup") }
        }
    }
}
