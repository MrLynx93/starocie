package pl.starocie.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.starocie.domain.Item

/**
 * The price a thing went for, and nothing else.
 *
 * Deliberately only about selling: taking money is the fast path and it stays a
 * single field over the list. Anything else you might do to an item — looking at
 * what it cost, taking it out of stock without a sale — lives on the stock screen,
 * where there is room to read before acting.
 *
 * Shared by the sell search and the stock detail, so one sale is one motion no
 * matter which of them you came from.
 */
@Composable
internal fun SellDialog(
    item: Item,
    state: SellUiState,
    onPriceChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSoldCompletelyChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
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
                    label = { Text("Sprzedaliśmy za") },
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
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = state.canConfirm) { Text("Sprzedane") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
