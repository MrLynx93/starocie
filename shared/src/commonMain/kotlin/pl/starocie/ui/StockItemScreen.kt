package pl.starocie.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import pl.starocie.domain.ItemStatus
import pl.starocie.domain.LedgerRepository
import pl.starocie.domain.Money
import pl.starocie.domain.format
import pl.starocie.domain.parseMoney
import pl.starocie.domain.toInputText

/**
 * One thing in stock, in full: what is known about it, what can still be corrected,
 * and the three things you can do about it.
 *
 * The facts scroll — the day it came home, what it has taken so far — and the two
 * prices sit under them as fields, because both are still decisions.
 * One gets mistyped or skipped in a hurry; the other changes every time a thing
 * sits around unsold. Neither has a save button.
 *
 * Selling is one tap at the price standing in that second field, with nothing asked
 * in between: the number is already on screen and already editable, so a dialog
 * would be a second place to type it. Without a price there is nothing to sell at,
 * so the button waits for one.
 *
 * A lot is the one thing that still opens a dialog, because a piece of it goes at
 * its own price and only somebody who is there can say whether that was the last of
 * it. This is now the only way to that dialog: the list opens the item rather than
 * offering to sell it from under your thumb.
 *
 * Removing used to sit inside the sell dialog, one thumb-width from the price
 * field — a screen you reach by searching for something to sell is the wrong place
 * to delete one instead. Here there is room to read what a thing cost before
 * deciding, and the red button is a deliberate stop rather than a near miss.
 *
 * It leaves by itself the moment the item stops being in stock or stops existing,
 * so a completed sale or a deletion lands you back in the list it came from. A lot
 * sold in part is still in stock, so the screen stays and shows one more sale.
 */
@Composable
fun StockItemScreen(itemId: String, onDone: () -> Unit, selling: Boolean = true) {
    val repository: LedgerRepository = koinInject()
    val ledger by repository.ledger.collectAsState()
    val viewModel: SellViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val item = ledger.itemById(itemId)
    var confirmingRemoval by remember { mutableStateOf(false) }
    // The price it would go out at, held while the question is on screen: the field
    // behind the dialog keeps saving, and the answer must be about the number that
    // was asked about.
    var confirmingSale by remember { mutableStateOf<Money?>(null) }

    // Waiting to have seen it in stock first: the ledger is empty for the instant
    // before the first snapshot arrives, and popping on that would close the screen
    // as it opens. A deletion takes the item away entirely, which lands here too.
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(item?.status) {
        if (item != null && item.status == ItemStatus.IN_STOCK) seen = true else if (seen) onDone()
    }

    if (item == null) return

    val stats = remember(ledger, item) { ledger.itemStats(item) }
    val buy = item.buyId?.let { ledger.buyById(it) }

    // Straight to the record: on the buy form a photo waits with the rest of the
    // draft, but here the item already exists, so backing out of the camera is the
    // only way not to change it.
    val takePhoto = rememberPhotoCapture { photo ->
        if (photo != null) viewModel.setPhoto(item.id, photo)
    }

    // What was paid is the buy's, not the item's — so with several things in one
    // buy the field edits the price of the box, and has to say that it does.
    val isPartOfABox = item.buyId != null && ledger.itemCountOfBuy(item.buyId) > 1

    // Both fields are held here rather than inside them, so "Sprzedaj" can read what
    // has been *typed*: the field saves half a second after the typing stops, and a
    // price entered and sold on in one motion must not go out at the old number.
    var paidText by remember(item.id) { mutableStateOf(buy?.price?.toInputText() ?: "") }
    var askingText by remember(item.id) { mutableStateOf(item.price?.toInputText() ?: "") }
    val asking = parseMoney(askingText)

    ScreenColumn {
        // What the item is scrolls; what you can do about it stays put at the
        // bottom, so the three buttons are always in the same place under the thumb.
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            Text(item.name, style = MaterialTheme.typography.headlineSmall)

            if (item.splittable) {
                // What is left is the number that matters when the lot is half gone,
                // and the one the dialog is about to open on.
                val left = ledger.piecesLeft(item)
                Text(
                    if (left < item.quantity) {
                        "Zostało $left z ${item.quantity} szt."
                    } else {
                        "${item.quantity} szt. · sprzedaje się po kawałku"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(16.dp))

            // The same camera target as the buy form, not a read-only view: a thing
            // photographed in a hurry at a stall is exactly the thing worth shooting
            // again in better light, and until now there was nowhere to do it.
            PhotoArea(
                photo = item.photo,
                onCapture = takePhoto,
                onClear = { viewModel.setPhoto(item.id, null) },
                modifier = Modifier.height(220.dp),
            )

            Spacer(Modifier.height(20.dp))

            // The date leads: it is the one fact here that was never a choice, and
            // it says which day's trip this thing came home from.
            Detail("Kupiliśmy dnia", item.date.asText())

            if (stats.sellCount > 0) {
                Detail(
                    label = "Sprzedaliśmy do tej pory",
                    value = if (item.splittable) {
                        "${stats.soldQuantity} szt. · ${stats.proceeds.format()}"
                    } else {
                        "${stats.sellCount} × · ${stats.proceeds.format()}"
                    },
                )
            }

            Spacer(Modifier.height(20.dp))

            // Both prices are still decisions rather than records — one was mistyped
            // or forgotten, the other changes every time a thing sits unsold — so
            // they are fields, and they sit together under the facts.
            MoneyField(
                label = if (isPartOfABox) "Całą paczkę kupiliśmy za" else "Kupiliśmy za",
                text = paidText,
                onTextChange = { paidText = it },
                saved = buy?.price,
                placeholder = "Nie wiemy",
                // An exact cost and a guess must never look alike: with several
                // things in one buy, this field is the box's price and the item's
                // own cost is only a share of it.
                hint = when {
                    isPartOfABox && stats.cost != null ->
                        "Na ten przedmiot wypada z niej ok. ${stats.cost.format()}."
                    isPartOfABox -> "Cena paczki dzieli się na wszystko, co w niej było."
                    item.buyId == null ->
                        "Nie zapisaliśmy zakupu. Wpiszmy cenę, a policzymy zysk."
                    else -> null
                },
                onSave = { viewModel.setPaidPrice(item.id, it) },
            )

            Spacer(Modifier.height(10.dp))

            MoneyField(
                // On a lot this is the price of one piece — the sell dialog
                // multiplies it by the count — so the label says which it is.
                label = if (item.splittable) "Chcemy sprzedać za sztukę" else "Chcemy sprzedać za",
                text = askingText,
                onTextChange = { askingText = it },
                saved = item.price,
                placeholder = "Jeszcze nie wiemy",
                // A lot is sold a piece at a time, at a price this field never held.
                hint = "Sprzedajemy po kawałku — przy sprzedaży podamy, ile sztuk."
                    .takeIf { item.splittable },
                onSave = { viewModel.setAskingPrice(item.id, it) },
            )

            // A failed write leaves the screen where it is, so it has to say why
            // rather than looking like nothing was pressed.
            state.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // One tap sells, at the price standing in the field above. Nothing is asked
        // in between: the price is already on screen and editable, so a dialog
        // repeating it back would only be a second place to type the same number.
        //
        // A lot is the exception, and the only reason the dialog still exists: a
        // piece goes at its own price, and somebody has to say whether that was the
        // last of it. Reaching it is what "Sprzedaj" does here, now that the list
        // behind this screen opens the item instead.
        //
        // A giełda that has already happened is the one place it is missing: those
        // rows are a record of a day, and a sale started from one would be dated
        // today and land in today's takings, which is not the day you are reading.
        // Today's giełda is not that case — there the two days are the same one, and
        // a stall we are standing at is exactly where selling has to be a tap away.
        if (selling) {
            Button(
                onClick = {
                    if (item.splittable) {
                        viewModel.select(item, askingText)
                    } else {
                        confirmingSale = asking
                    }
                },
                enabled = item.splittable || asking != null,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Sprzedaj", fontWeight = FontWeight.Medium) }

            Spacer(Modifier.height(10.dp))
        }

        // Red, because it is the one button here that destroys something: it now
        // deletes the record rather than parking it out of sight, so it must not
        // look like the neutral way out sitting directly underneath it.
        OutlinedButton(
            onClick = { confirmingRemoval = true },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Usuń") }

        Spacer(Modifier.height(10.dp))

        BackButton(onDone)
    }

    state.selected?.let { selected ->
        SellDialog(
            item = selected,
            state = state,
            onPriceChange = viewModel::onPriceChange,
            onQuantityChange = viewModel::onSellQuantityChange,
            onSoldCompletelyChange = viewModel::onSoldCompletelyChange,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismiss,
        )
    }

    // Selling is one tap and then one answer: the price is already on screen, so the
    // question is only whether that is the number it goes at. A lot asks its own
    // questions in the sell dialog and never comes through here.
    confirmingSale?.let { price ->
        AlertDialog(
            onDismissRequest = { confirmingSale = null },
            title = { Text("Sprzedajemy?") },
            text = { Text("Czy chcesz sprzedać ten przedmiot za ${price.format()}?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingSale = null
                    viewModel.sell(item, price)
                }) { Text("Sprzedaj") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingSale = null }) { Text("Anuluj") }
            },
        )
    }

    if (confirmingRemoval) {
        AlertDialog(
            onDismissRequest = { confirmingRemoval = false },
            title = { Text("Usunąć przedmiot?") },
            text = { Text("Tego nie da się cofnąć") },
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
