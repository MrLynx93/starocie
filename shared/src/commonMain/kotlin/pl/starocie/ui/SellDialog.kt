package pl.starocie.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.starocie.domain.Item

/**
 * Agreeing to a sale: what it goes for, and — on a lot — how many pieces are going.
 *
 * **The price is a field, not a sentence read back.** The asking price fills it in,
 * because that is nearly always what a thing goes for; but haggling is what happens
 * at a stall, and the number agreed across the table is the one that has to be
 * recorded. Correcting the asking price first and then selling would be two motions
 * for one moment, and the second of them would be the one that gets forgotten.
 *
 * On a lot the count leads, because it is the thing the price depends on and the
 * thing only somebody standing at the stall knows. It starts at **one** — one is
 * what a sale out of a lot of twenty usually is, and the commonest answer should
 * cost no taps.
 *
 * The price then follows the count, multiplying up from what one piece was asked
 * for: three plates at the asking price is three times it, and that is arithmetic
 * the app can do without being retyped.
 *
 * Taking the last pieces ticks "sprzedaliśmy już wszystkie" itself and stops
 * offering it as a choice — there is nothing left for it to write off. Below that,
 * it is still worth asking: it says the rest was kept, lost or given away.
 *
 * **The count has no ceiling.** Selling more pieces than the record says there were
 * corrects the record, because a box counted at a stall comes out one short more
 * often than a piece appears from nowhere, and the pieces in your hand outrank a
 * number typed in a hurry.
 *
 * A single thing sees none of that: one field, and the two buttons under it.
 */
@Composable
internal fun SellDialog(
    item: Item,
    state: SellUiState,
    onPriceChange: (String) -> Unit,
    onQuantityChange: (Int) -> Unit,
    onSoldCompletelyChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column {
                if (item.splittable) {
                    PieceCounter(
                        count = state.sellQuantity,
                        left = state.piecesLeft,
                        onChange = onQuantityChange,
                    )

                    // Selling past the end of a lot is not an error to block: the
                    // pieces are in your hand and the record is what is wrong.
                    state.correctedQuantity?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Było ich więcej, niż zapisaliśmy — poprawimy paczkę na $it szt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = state.priceText,
                    onValueChange = onPriceChange,
                    singleLine = true,
                    // On a lot the field is the total for the pieces going, not the
                    // price of one, and the label is the only thing that says so.
                    label = {
                        Text(
                            if (item.splittable) {
                                "Sprzedajemy ${sztuki(state.sellQuantity)} za"
                            } else {
                                "Sprzedajemy za"
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (item.splittable) {
                    Spacer(Modifier.height(8.dp))

                    // Taking the last pieces ticks this itself and stops offering
                    // the choice: there would be nothing left for it to write off,
                    // so the only honest state is the one the count already implies.
                    val closes = state.quantityClosesTheItem
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = closes || state.soldCompletely,
                            onCheckedChange = if (closes) null else onSoldCompletelyChange,
                        )
                        Text("Sprzedaliśmy już wszystkie")
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
            Button(onClick = onConfirm, enabled = state.canConfirm) { Text("Sprzedaj") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

/**
 * How many pieces are going, as two buttons and a number.
 *
 * A stepper rather than a field, because the answer is nearly always one or two
 * away from where it starts and a keyboard over a dialog would cost more than the
 * taps it saves. Only the bottom end stops — there is no selling half a thing —
 * while the top keeps going past [left] and corrects the lot instead.
 */
@Composable
private fun PieceCounter(count: Int, left: Int, onChange: (Int) -> Unit) {
    Column {
        Text(
            "Ile sztuk sprzedajemy?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { onChange(count - 1) },
                enabled = count > 1,
            ) { Icon(Icons.Filled.Remove, contentDescription = "Mniej") }

            Text(
                count.toString(),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(64.dp),
            )

            FilledTonalIconButton(
                onClick = { onChange(count + 1) },
            ) { Icon(Icons.Filled.Add, contentDescription = "Więcej") }

            Spacer(Modifier.width(12.dp))

            Text(
                "z $left",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
