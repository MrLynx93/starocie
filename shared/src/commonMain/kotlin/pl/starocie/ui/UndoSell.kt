package pl.starocie.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.starocie.domain.Money
import pl.starocie.domain.Sell
import pl.starocie.domain.format
import pl.starocie.domain.priceOf

/**
 * Taking one sale back — the wrong row tapped at the stall, or a buyer who changed
 * their mind after "Sprzedaj" had already been pressed.
 *
 * Red, like "Usuń", because it erases a record: what the sale went for and when stop
 * existing, and that day's takings drop by as much. A text button rather than an
 * outlined one, because it belongs to one sale among what may be several, and a lot
 * sold in eight parts must not become a column of eight full-width alarms.
 *
 * So this is for a sale with others beside it. A thing that went in one sale is taken
 * back from a [DestructiveButton] pinned at the bottom of the sold screen instead,
 * exactly where and how "Usuń" sits on the magazyn's.
 */
@Composable
internal fun UndoSellButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = modifier,
    ) { Text(label) }
}

/**
 * Asks before the sale goes, and says both halves of what that does: where the pieces
 * end up, and that the money stops counting.
 *
 * A sale of several pieces asks how many come back, starting at all of them — the
 * wrong row tapped is the commoner mistake — and stepping down to the buyer who
 * returned three of the ten rings. The text follows the count.
 *
 * [backInStock] is false only for a lot another sale still closes, where nothing comes
 * back to the magazyn and only the money goes.
 */
@Composable
internal fun UndoSellDialog(
    sell: Sell,
    splittable: Boolean,
    backInStock: (pieces: Int) -> Boolean,
    onConfirm: (pieces: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var pieces by remember(sell.id, sell.quantity) { mutableIntStateOf(sell.quantity) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cofnąć sprzedaż?") },
        text = {
            Column {
                if (sell.quantity > 1) {
                    PieceCounter(
                        label = "Ile sztuk cofamy?",
                        count = pieces,
                        left = sell.quantity,
                        onChange = { pieces = it.coerceIn(1, sell.quantity) },
                        max = sell.quantity,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    undoSellText(
                        price = sell.priceOf(pieces),
                        pieces = pieces,
                        splittable = splittable,
                        backInStock = backInStock(pieces),
                        wholeSale = pieces >= sell.quantity,
                    ),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pieces) }) { Text("Cofnij") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

/**
 * [price] is what leaves the books — the whole sale's, or [pieces]' share of it when
 * only some come back and the sale itself stays.
 */
internal fun undoSellText(
    price: Money,
    pieces: Int,
    splittable: Boolean,
    backInStock: Boolean,
    wholeSale: Boolean = true,
): String {
    if (!wholeSale) {
        val money = "naszych rachunków zniknie ${price.format()}."
        return if (backInStock) "${piecesComeBack(pieces)} do magazynu, a z $money" else "Z $money"
    }
    val sale = "sprzedaż za ${price.format()} zniknie z naszych rachunków."
    return when {
        !backInStock -> "Ta $sale"
        splittable -> "${piecesComeBack(pieces)} do magazynu, a $sale"
        else -> "Przedmiot wróci do magazynu, a $sale"
    }
}

/**
 * "1 sztuka wróci", "3 sztuki wrócą", "5 sztuk wróci" — the verb follows the number
 * as well as the noun does, with the same teens exception as [sztuki].
 */
internal fun piecesComeBack(count: Int): String {
    val tail = count % 10
    val teens = count % 100 in 12..14
    return when {
        count == 1 -> "1 sztuka wróci"
        tail in 2..4 && !teens -> "$count sztuki wrócą"
        else -> "$count sztuk wróci"
    }
}
