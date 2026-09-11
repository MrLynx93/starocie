package pl.starocie.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pl.starocie.domain.Money
import pl.starocie.domain.Sell
import pl.starocie.domain.format

/**
 * Taking one sale back — the wrong row tapped at the stall, or a buyer who changed
 * their mind after "Sprzedaj" had already been pressed.
 *
 * Red, like "Usuń", because it erases a record: what the sale went for and when stop
 * existing, and that day's takings drop by as much. A text button rather than an
 * outlined one, because it belongs to one sale among what may be several, and a lot
 * sold in eight parts must not become a column of eight full-width alarms.
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
 * [backInStock] is false only for a lot another sale still closes, where nothing comes
 * back to the magazyn and only the money goes.
 */
@Composable
internal fun UndoSellDialog(
    sell: Sell,
    splittable: Boolean,
    backInStock: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cofnąć sprzedaż?") },
        text = { Text(undoSellText(sell.price, sell.quantity, splittable, backInStock)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Cofnij") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

internal fun undoSellText(price: Money, pieces: Int, splittable: Boolean, backInStock: Boolean): String {
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
