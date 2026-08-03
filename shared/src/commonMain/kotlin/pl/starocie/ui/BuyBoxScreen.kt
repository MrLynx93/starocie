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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pl.starocie.domain.format

/**
 * One payment covering several things. The price is split across the contents by
 * asking price, and the split is shown live so it is never a surprise afterwards.
 */
@Composable
fun BuyBoxScreen(onDone: () -> Unit) {
    val viewModel: BuyBoxViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    var itemName by remember { mutableStateOf("") }
    var itemPrice by remember { mutableStateOf("") }
    var itemSplittable by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }

    // Adding clears the row and returns the cursor to the name, so the contents of
    // a box go in as one run of typing.
    LaunchedEffect(state.drafts.size) {
        if (state.drafts.isNotEmpty()) runCatching { nameFocus.requestFocus() }
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {

            Text("Kupuję pudło", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Jedna cena za całość, potem co w tym było.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.totalText,
                    onValueChange = viewModel::onTotalChange,
                    singleLine = true,
                    label = { Text("Zapłacono") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    singleLine = true,
                    label = { Text("Skąd (opcj.)") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    singleLine = true,
                    label = { Text("Co w tym było") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(2f).focusRequester(nameFocus),
                )
                OutlinedTextField(
                    value = itemPrice,
                    onValueChange = { itemPrice = it },
                    singleLine = true,
                    label = { Text("Cena wyw.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                FilterChip(
                    selected = itemSplittable,
                    onClick = { itemSplittable = !itemSplittable },
                    label = { Text("Na sztuki") },
                )
                TextButton(
                    enabled = itemName.isNotBlank(),
                    onClick = {
                        viewModel.addDraft(itemName, itemPrice, itemSplittable)
                        itemName = ""
                        itemPrice = ""
                        itemSplittable = false
                    },
                ) { Text("Dodaj i następna") }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.preview) { index, (draft, share) ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(draft.name, fontWeight = FontWeight.Medium)
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

            state.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDone,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Anuluj") }
                Button(
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(2f).height(52.dp),
                ) { Text("Zapisz pudło", fontWeight = FontWeight.Medium) }
            }
        }
    }
}
