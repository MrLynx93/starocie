package pl.starocie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import pl.starocie.domain.ItemStatus
import pl.starocie.domain.LedgerRepository
import pl.starocie.domain.Sell
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
 * The name is a field too, and it is the heading: a thing named in a hurry at a
 * stall is exactly what wants correcting later, and it is the one field that has to
 * hold something, being how the thing is found when it is finally sold. So a blank
 * writes nothing and the old name stands.
 *
 * Selling opens one question, seeded with the asking price standing in that second
 * field: what does it actually go for? A stall haggles, so the number agreed across
 * the table is not always the one written down — and correcting the ask first and
 * then selling would be two motions for one moment, the second of them the one that
 * gets forgotten. So the dialog carries the price as a field rather than reading it
 * back as a sentence, and the button no longer waits for one: an unpriced thing is
 * priced there, in the same breath as it is sold.
 *
 * A lot asks more in the same dialog, because a piece of it goes at its own price
 * and only somebody who is there can say whether that was the last of it. This
 * screen is the only way to that dialog: the list opens the item rather than
 * offering to sell it from under your thumb.
 *
 * Removing used to sit inside the sell dialog, one thumb-width from the price
 * field — a screen you reach by searching for something to sell is the wrong place
 * to delete one instead. Here there is room to read what a thing cost before
 * deciding, and the red button is a deliberate stop rather than a near miss.
 *
 * A lot that has already sold some of itself gets a different button in that place:
 * deleting it would leave those sales with nothing to resolve to, so what we paid
 * would vanish from the books and the profit we made on them would rise to fill the
 * gap. "Sprzedaliśmy już wszystko" closes the lot instead — it leaves the magazyn,
 * the buy and every sale stay, and the pieces that never went keep their share of
 * the cost as the loss it is. Only a thing with nothing sold against it can be
 * deleted, because only then is there nothing to destroy.
 *
 * It leaves by itself the moment the item stops being in stock or stops existing,
 * so a completed sale or a deletion lands you back in the list it came from. A lot
 * sold in part is still in stock, so the screen stays and shows one more sale.
 *
 * Each of those sales is a line with "Cofnij" beside it. A lot sold in part never
 * reaches the sold list, so a sale recorded against it by mistake — the wrong lot
 * tapped, a buyer who walked away — can only be taken back from here.
 */
@Composable
fun StockItemScreen(itemId: String, onDone: () -> Unit, selling: Boolean = true) {
    val repository: LedgerRepository = koinInject()
    val ledger by repository.ledger.collectAsState()
    val viewModel: SellViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val item = ledger.itemById(itemId)
    var confirmingRemoval by remember { mutableStateOf(false) }
    var confirmingSoldOut by remember { mutableStateOf(false) }
    var undoing by remember { mutableStateOf<Sell?>(null) }

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
    // What is left is the number that matters when the lot is half gone: the one the
    // sell dialog opens on, and the one that closing it writes off.
    val left = remember(ledger, item) { ledger.piecesLeft(item) }
    val sells = remember(ledger, item) { ledger.sellsOfItem(item.id) }

    // Straight to the record: on the buy form a photo waits with the rest of the
    // draft, but here the item already exists, so backing out of the camera is the
    // only way not to change it.
    val takePhoto = rememberPhotoCapture { photo ->
        if (photo != null) viewModel.setPhoto(item.id, photo)
    }

    // What was paid is the buy's, not the item's — so with several things in one
    // buy the field edits the price of the box, and has to say that it does.
    val isPartOfABox = item.buyId != null && ledger.itemCountOfBuy(item.buyId) > 1

    // A lot bought on its own is priced by the piece here, in the buy form's own
    // words: what one of them cost is the number somebody remembers paying, and the
    // total is a multiplication they did at the stall. The record still holds what
    // was handed over — the field is a way of typing it, not a second number.
    //
    // A lot out of a box is the exception, because there the field is the box's
    // price and a box was paid for once, whatever was in it.
    val pricedPerPiece = item.splittable && !isPartOfABox
    val paidShown = buy?.price?.let { if (pricedPerPiece) it / item.quantity else it }

    // How many there are is a correction like the prices are, right up until the
    // first piece goes: a crate counted in a hurry comes out wrong, and a lot
    // entered as the one thing it looked like is wrong from the start. After a sale
    // it is no longer ours to type — that sale was measured against this count — and
    // the lot corrects itself the one way it always has, by being oversold.
    val countIsOurs = stats.sellCount == 0

    // Every field's text is held by the screen rather than by the field itself, so
    // it survives the ledger echoing each write back.
    var nameText by remember(item.id) { mutableStateOf(item.name) }

    // The two prices are held here for a second reason, so "Sprzedaj" can hand the
    // dialog what has been *typed*: the field saves half a second after the typing
    // stops, and a price entered and sold on in one motion must not open the dialog
    // on the old number.
    //
    // The paid field is keyed on the item alone, count changes included: what it
    // holds is a price per piece, and correcting the count moves the buy's total to
    // match rather than the money each piece cost. So the number in it is the same
    // number afterwards — a lot of three at 30,00 zł becoming a lot of four still
    // reads 30,00 zł, and only the label above and the total below it change.
    var paidText by remember(item.id) { mutableStateOf(paidShown?.toInputText() ?: "") }
    var askingText by remember(item.id) { mutableStateOf(item.price?.toInputText() ?: "") }
    var quantityText by remember(item.id) { mutableStateOf(item.quantity.toString()) }

    ScreenColumn {
        // What the item is scrolls; what you can do about it stays put at the
        // bottom, so the three buttons are always in the same place under the thumb.
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            // The name is the field rather than a heading with the field somewhere
            // below it: one name in two places on one screen is one of them
            // disagreeing with the other while it is being typed. A giełda names
            // itself the same way on its own screen.
            //
            // It is correctable for the same reason the prices under it are — it is
            // typed one-handed while somebody waits to be paid, and it comes out as
            // "lampa" or as a thumb's worth of nonsense. Unlike them it is the one
            // thing the app cannot shrug at, being how the thing is found when it is
            // finally sold, so a wrong one costs a sale rather than a figure.
            NameField(
                label = "Nazwa",
                text = nameText,
                onTextChange = { nameText = it },
                saved = item.name,
                placeholder = "Jak to nazwiemy?",
                // Only while it is empty, and it says why nothing was written: there
                // is no honest blank here the way there is for a cost we never knew.
                hint = "Bez nazwy nie znajdziemy przedmiotu przy sprzedaży — zostawiamy starą."
                    .takeIf { nameText.isBlank() },
                onSave = { viewModel.nameItem(item.id, it) },
            )

            Spacer(Modifier.height(4.dp))

            // Only where the count is a read-out. Where it is a field it says the
            // same thing further down and can put it right, and one number in two
            // places on one screen is one of them disagreeing while it is typed.
            //
            // What is left, and nothing about selling by the piece: this line is only
            // ever drawn once a piece has gone, so it is a lot that is plainly being
            // sold that way and saying so is telling somebody what they just did.
            if (item.splittable && !countIsOurs) {
                Text(
                    "Zostało $left z ${item.quantity} szt.",
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

                // The sales themselves, so one recorded by mistake can be taken back:
                // a lot sold in part is not on the sold list, and this is the only
                // screen it is on.
                sells.forEach { sell ->
                    SaleLine(
                        sell = sell,
                        splittable = item.splittable,
                        onUndo = { undoing = sell },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Both prices are still decisions rather than records — one was mistyped
            // or forgotten, the other changes every time a thing sits unsold — so
            // they are fields, and they sit together under the facts.
            //
            // The count shares the line with what was paid, the way it shares one
            // with the name on the buy form: a narrow box at the end of a wide one,
            // rather than a stub sitting alone on a row of its own. The row is drawn
            // whether or not the count is in it — a lone weighted field is a full
            // line — so there is one paid field here and not two to keep in step.
            val typedPaid = parseMoney(paidText)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MoneyField(
                    // "za szt." rather than "za sztukę", and only here: sharing the
                    // line leaves the label about 197 dp where the full phrase wants
                    // 208, so the word that survives is the one the width can afford.
                    // It is the same exception `rzeczy(n)` is on the giełda rows —
                    // a width buying a shorter word, not a second name for the thing.
                    label = when {
                        isPartOfABox -> "Całą paczkę kupiliśmy za"
                        pricedPerPiece -> "Kupiliśmy po cenie za szt."
                        else -> "Kupiliśmy za"
                    },
                    text = paidText,
                    onTextChange = { paidText = it },
                    saved = paidShown,
                    placeholder = "Nie wiemy",
                    // An exact cost and a guess must never look alike: with several
                    // things in one buy, this field is the box's price and the item's
                    // own cost is only a share of it.
                    //
                    // A lot reads its total back instead, the way the buy form does: a
                    // pile's total typed into a per-piece field is otherwise invisible
                    // until the profit is wrong weeks later.
                    hint = when {
                        isPartOfABox && stats.cost != null ->
                            "Na ten przedmiot wypada z niej ok. ${stats.cost.format()}."
                        isPartOfABox -> "Cena paczki dzieli się na wszystko, co w niej było."
                        pricedPerPiece && typedPaid != null ->
                            "Kupiliśmy ${sztuki(item.quantity)} za ${(typedPaid * item.quantity).format()}"
                        item.buyId == null ->
                            "Wpisz cenę zakupu, żeby policzyć realny zysk"
                        else -> null
                    },
                    onSave = {
                        viewModel.setPaidPrice(
                            item.id,
                            it,
                            pieces = if (pricedPerPiece) item.quantity else 1,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )

                // A shade narrower than the buy form's share of its row, because the
                // field it sits beside carries a phrase where that one carries a name.
                if (countIsOurs) {
                    CountField(
                        label = "Sztuki",
                        text = quantityText,
                        onTextChange = { quantityText = it },
                        saved = item.quantity,
                        onSave = { viewModel.setQuantity(item.id, it) },
                        modifier = Modifier.weight(0.35f),
                    )
                }
            }

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

        // One tap opens the sale, with the price above already in its field: a thing
        // usually goes for what we are asking, and when it does not, the number that
        // was agreed belongs in the sale rather than in a correction made first and
        // then sold on. So nothing waits for a price here — the dialog is where one
        // is given.
        //
        // A lot asks its count there too: a piece goes at its own price, and somebody
        // has to say whether that was the last of it.
        //
        // A giełda that has already happened is the one place it is missing: those
        // rows are a record of a day, and a sale started from one would be dated
        // today and land in today's takings, which is not the day you are reading.
        // Today's giełda is not that case — there the two days are the same one, and
        // a stall we are standing at is exactly where selling has to be a tap away.
        if (selling) {
            Button(
                onClick = { viewModel.select(item, askingText) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Sprzedaj", fontWeight = FontWeight.Medium) }

            Spacer(Modifier.height(10.dp))
        }

        // Two different acts in one place, and which one it is depends on whether
        // anything has gone yet.
        //
        // With a sale behind it, the way out is to close the lot: the rest is not
        // coming back, but the money that came in is real and so is what we paid for
        // the pieces it came from. Deleting would strand those sales — an
        // unresolvable one is set against no cost at all — so the purchase would
        // drop out of the books and the profit would rise to meet it.
        //
        // With nothing sold there is nothing to strand, so the record can go, and it
        // goes in red: it is the one button here that destroys something, and it
        // must not look like the neutral way out sitting directly underneath it.
        if (stats.sellCount > 0) {
            OutlinedButton(
                onClick = { confirmingSoldOut = true },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Sprzedaliśmy już wszystko") }
        } else {
            DestructiveButton(label = "Usuń", onClick = { confirmingRemoval = true })
        }

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

    // It asks, like the deletion does, but about something else: this one is a
    // statement about the lot rather than an erasure, so it says where the pieces
    // that never went have got to and what stays behind.
    if (confirmingSoldOut) {
        AlertDialog(
            onDismissRequest = { confirmingSoldOut = false },
            title = { Text("Sprzedaliśmy już wszystko?") },
            text = {
                Text(
                    buildString {
                        if (item.splittable && left < item.quantity) {
                            append("Zostało $left z ${item.quantity} szt. Reszty nie sprzedamy — ")
                        } else {
                            append("Reszty nie sprzedamy — ")
                        }
                        append("przedmiot zniknie z magazynu, ")
                        append("a to, co już sprzedaliśmy, zostanie w rachunkach.")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingSoldOut = false
                    viewModel.markSoldOut(item)
                }) { Text("Zapisz") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingSoldOut = false }) { Text("Anuluj") }
            },
        )
    }

    undoing?.let { sell ->
        UndoSellDialog(
            sell = sell,
            splittable = item.splittable,
            backInStock = ledger.statusAfterUndoing(sell) == ItemStatus.IN_STOCK,
            onConfirm = {
                undoing = null
                viewModel.undoSell(sell.id)
            },
            onDismiss = { undoing = null },
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

/**
 * One sale of a thing still in stock — the day, the pieces, the money — with the way
 * to take it back beside it.
 *
 * "Cofnij" alone, where the sold screen says "Cofnij sprzedaż": this line is plainly a
 * sale already, and the longer label would take the width the figures need.
 */
@Composable
private fun SaleLine(sell: Sell, splittable: Boolean, onUndo: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildString {
                append(sell.date.asText())
                if (splittable) append(" · ${sell.quantity} szt.")
                append(" · ${sell.price.format()}")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        UndoSellButton(label = "Cofnij", onClick = onUndo)
    }
}
