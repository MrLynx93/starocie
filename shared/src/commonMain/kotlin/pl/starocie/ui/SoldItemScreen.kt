package pl.starocie.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.datetime.LocalDate
import pl.starocie.domain.ItemStats
import pl.starocie.domain.ItemStatus
import pl.starocie.domain.LedgerRepository
import pl.starocie.domain.Money
import pl.starocie.domain.Sell
import pl.starocie.domain.format
import pl.starocie.domain.parseMoney
import pl.starocie.domain.toInputText

/**
 * One thing that has already gone, opened from the sold list — the mirror of the
 * magazyn's item screen, asking the questions that are still open once a thing is
 * no longer ours.
 *
 * Four numbers make the whole record and every one of them can be wrong: a price
 * fat-fingered while somebody waits for change, a thing entered the evening after
 * the market and so dated a day late. None of it is a decision any more, but all of
 * it is still a correction — so the four are fields rather than read-outs, and they
 * save themselves the way the prices in the magazyn do, with nothing to press.
 *
 * The name is a field as well, and it is the heading — the same one the magazyn's
 * screen carries. A thing typed in one-handed is as mistypeable as a price, and
 * here it is what this list is searched by; a blank writes nothing, a name being
 * the one thing an item has to have.
 *
 * The profit sits at the top and is recomputed from those fields as they change,
 * which is what tells you the correction landed. It is the only thing on the screen
 * that is not editable, because it is not a fact anybody entered.
 *
 * A lot that went in several sales gets a pair of fields per sale, since each one
 * happened on its own day for its own money. There is no "Usuń" here: deleting
 * belongs to the magazyn, where a thing still exists to be got rid of, and erasing
 * a sold item would only lose the proceeds it is the record of.
 *
 * What there is instead is "Cofnij sprzedaż": the answer to a sale that should never
 * have been recorded at all, which no field here can correct. A thing that went in one
 * sale gets it pinned above "Wstecz", red and full width, exactly where and how "Usuń"
 * sits on the magazyn's screen; a lot sold in parts gets a small one under each sale,
 * since one button at the bottom could not say which it meant. The sale goes, its
 * pieces come back into the magazyn, and the screen leaves with them unless another
 * sale still closes the lot.
 */
@Composable
fun SoldItemScreen(itemId: String, onDone: () -> Unit) {
    val repository: LedgerRepository = koinInject()
    val ledger by repository.ledger.collectAsState()
    val viewModel: SellViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val item = ledger.itemById(itemId)

    // The ledger is empty for the instant before the first snapshot arrives, so the
    // screen waits to have seen the item before treating its absence as a deletion
    // from the other phone — otherwise it would close itself as it opens.
    //
    // Going back into stock lands here too: once a sale is taken back there is
    // nothing sold left to correct, and the thing has its own screen in the magazyn.
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(item?.status) {
        if (item != null && item.status != ItemStatus.IN_STOCK) seen = true else if (seen) onDone()
    }
    var undoing by remember { mutableStateOf<Sell?>(null) }

    if (item == null) return

    val stats = remember(ledger, item) { ledger.itemStats(item) }
    val buy = item.buyId?.let { ledger.buyById(it) }
    val sells = remember(ledger, item) { ledger.sellsOfItem(item.id) }

    // What was paid is the buy's, not the item's — so with several things in one
    // buy the field edits the price of the box, and has to say that it does.
    val isPartOfABox = item.buyId != null && ledger.itemCountOfBuy(item.buyId) > 1

    // A lot bought on its own is priced by the piece, in the same words as the buy
    // form and the magazyn's item screen: one label cannot mean two things. What was
    // handed over is still what the record holds — the field is a way of typing it.
    val pricedPerPiece = item.splittable && !isPartOfABox
    val paidShown = buy?.price?.let { if (pricedPerPiece) it / item.quantity else it }

    var nameText by remember(item.id) { mutableStateOf(item.name) }
    var paidText by remember(item.id) { mutableStateOf(paidShown?.toInputText() ?: "") }

    ScreenColumn {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // The same field the magazyn's screen carries, for the same reason and
            // in the same place: a name typed at a stall is as correctable as the
            // prices under it, and here it is what the sold list is searched by.
            // It is the thing's own name, so the heading is the field rather than a
            // second copy of it sitting above one.
            NameField(
                label = "Nazwa",
                text = nameText,
                onTextChange = { nameText = it },
                saved = item.name,
                placeholder = "Jak to nazwiemy?",
                hint = "Bez nazwy nie znajdziemy przedmiotu na liście — zostawiamy starą."
                    .takeIf { nameText.isBlank() },
                onSave = { viewModel.nameItem(item.id, it) },
            )

            Spacer(Modifier.height(8.dp))

            Text(
                profitLabel(stats),
                style = MaterialTheme.typography.titleMedium,
                color = if (stats.isALoss) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )

            // Only when there is one, and with no camera on it: an empty target on a
            // thing that is no longer ours would invite photographing somebody
            // else's. The bin stays — a picture of something gone is the first thing
            // worth dropping, and it is supplementary either way.
            if (item.photo != null) {
                Spacer(Modifier.height(16.dp))
                PhotoArea(
                    photo = item.photo,
                    onCapture = null,
                    onClear = { viewModel.setPhoto(item.id, null) },
                    modifier = Modifier.height(220.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            // A single thing has four fields and no ambiguity about which sale is
            // which, so the headings are noise: the labels on the fields already say
            // what each one is. A lot earns them back, having several sales to tell
            // apart and a total to say.
            if (item.splittable) {
                // In the order it happened: what we gave, then what we took.
                SectionLabel("Kupiliśmy")
            }

            DateField(
                label = "Kupiliśmy dnia",
                date = item.date,
                onDateChange = { viewModel.setBoughtDate(item.id, it) },
                hint = if (isPartOfABox) "Dotyczy tego przedmiotu, nie całej paczki." else null,
            )

            Spacer(Modifier.height(10.dp))

            val typedPaid = parseMoney(paidText)
            MoneyField(
                label = when {
                    isPartOfABox -> "Całą paczkę kupiliśmy za"
                    // Abbreviated to match the magazyn's item screen, where sharing a
                    // line with the count is what shortens it. Same field, later in
                    // the same thing's life, so it has to read the same.
                    pricedPerPiece -> "Kupiliśmy po cenie za szt."
                    else -> "Kupiliśmy za"
                },
                text = paidText,
                onTextChange = { paidText = it },
                saved = paidShown,
                placeholder = "Nie wiemy",
                // An exact cost and a guess must never look alike: with several
                // things in one buy this is the box's price, and the item's own cost
                // is only a share of it.
                //
                // A lot reads its total back instead: a pile's total typed into a
                // per-piece field is otherwise invisible until the profit is wrong.
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
            )

            Spacer(Modifier.height(24.dp))

            // With several sales the heading carries the total, pieces and money, which
            // is why there is no separate line adding the sales up underneath them. With
            // one it is a bare "Sprzedaliśmy", like "Kupiliśmy" above: the sale's own
            // field reads its total back, and the same sentence twice is noise.
            if (item.splittable) {
                SectionLabel(
                    if (sells.size > 1) {
                        "Sprzedaliśmy ${sztuki(stats.soldQuantity)} za ${stats.proceeds.format()} " +
                            "w ${sells.size} kawałkach"
                    } else {
                        "Sprzedaliśmy"
                    },
                )
            }

            // Nothing here means the sale it was resolved by is gone, which the
            // screen says rather than showing an empty heading and no explanation.
            if (sells.isEmpty()) {
                Text(
                    "Nie mamy zapisanej sprzedaży tego przedmiotu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            sells.forEachIndexed { index, sell ->
                if (index > 0) Spacer(Modifier.height(20.dp))
                SaleFields(
                    sell = sell,
                    // A lot's sales differ by how many pieces went, and that is what
                    // makes one of them tellable from the next.
                    caption = when {
                        sells.size == 1 -> null
                        item.splittable -> "${index + 1}. sprzedaż · ${sell.quantity} szt."
                        else -> "${index + 1}. sprzedaż"
                    },
                    onDateChange = { viewModel.setSellDate(sell.id, it) },
                    onPriceSave = { text, pieces -> viewModel.setSellPrice(sell.id, text, pieces) },
                    // Only where there are several to choose between: a single sale is
                    // taken back from the pinned button below instead.
                    onUndo = if (sells.size > 1) {
                        { undoing = sell }
                    } else {
                        null
                    },
                )
            }

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

        // A thing that went in one sale has exactly one sale to take back, so the
        // button sits where "Usuń" sits on the magazyn's screen and looks the same:
        // pinned above the way out, red, the one control here that erases something.
        // A lot sold in parts keeps one under each sale instead — a single button down
        // here could not say which of them it meant.
        sells.singleOrNull()?.let { sell ->
            DestructiveButton(label = "Cofnij sprzedaż", onClick = { undoing = sell })
            Spacer(Modifier.height(10.dp))
        }

        BackButton(onDone)
    }

    undoing?.let { sell ->
        UndoSellDialog(
            sell = sell,
            splittable = item.splittable,
            backInStock = { ledger.statusAfterUndoing(sell, it) == ItemStatus.IN_STOCK },
            onConfirm = {
                undoing = null
                viewModel.undoSell(sell.id, it)
            },
            onDismiss = { undoing = null },
        )
    }
}

/**
 * The day and the price of one sale, which is the whole of what a sale is once it
 * has happened.
 *
 * The text is held here, keyed by the sale, so a lot's several sales cannot share a
 * field between them — and so an edit survives the ledger echoing the write back.
 *
 * A sale of several pieces is typed **per piece**, in the words the paid field above
 * uses — "Sprzedaliśmy po cenie za szt." over "Sprzedaliśmy 10 sztuk za 150,00 zł" —
 * because ten rings went at one ring's price, and that is the number anybody
 * remembers. `Sell.price` is still the total: the field multiplies on the way in and
 * divides on the way out, so a total that will not divide loses the odd grosz to the
 * display only, a price shown and left alone writing nothing.
 */
@Composable
private fun SaleFields(
    sell: Sell,
    caption: String?,
    onDateChange: (LocalDate) -> Unit,
    /** The typed text, and how many pieces it is the price of. */
    onPriceSave: (text: String, pieces: Int) -> Unit,
    /** Null when this is the only sale, which the screen's pinned button takes back. */
    onUndo: (() -> Unit)?,
) {
    val perPiece = sell.quantity > 1
    val shown = if (perPiece) sell.price / sell.quantity else sell.price
    // Keyed by the count as well: a sale joined or partly taken back has a different
    // price per piece from the one the field was seeded with.
    var priceText by remember(sell.id, sell.quantity) { mutableStateOf(shown.toInputText()) }

    Column {
        caption?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        DateField(
            label = "Sprzedaliśmy dnia",
            date = sell.date,
            onDateChange = onDateChange,
        )

        Spacer(Modifier.height(10.dp))

        val typed = parseMoney(priceText)
        MoneyField(
            label = if (perPiece) "Sprzedaliśmy po cenie za szt." else "Sprzedaliśmy za",
            text = priceText,
            onTextChange = { priceText = it },
            saved = shown,
            placeholder = "Za ile poszło",
            hint = if (perPiece && typed != null) {
                "Sprzedaliśmy ${sztuki(sell.quantity)} za ${(typed * sell.quantity).format()}"
            } else {
                null
            },
            onSave = { onPriceSave(it, if (perPiece) sell.quantity else 1) },
        )

        // Under the sale it takes back, so each of a lot's sales carries its own and
        // nothing has to ask which one was meant. It names what it undoes, because
        // beneath a price field a bare "Cofnij" reads as undoing the typing.
        onUndo?.let {
            UndoSellButton(
                label = "Cofnij sprzedaż",
                onClick = it,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    HorizontalDivider(Modifier.padding(top = 6.dp, bottom = 12.dp))
}

/** True when the sale went the wrong way. */
internal val ItemStats.isALoss: Boolean get() = profit.minor < 0

/**
 * What the sale came to, said rather than signed: a loss is something we lost, not
 * a gain with a minus in front of it, and a guess says so.
 */
internal fun profitLabel(stats: ItemStats): String {
    val approx = if (stats.profitIsEstimated) "ok. " else ""
    return if (stats.isALoss) {
        "Straciliśmy $approx${Money(-stats.profit.minor).format()}"
    } else {
        "Zarobiliśmy $approx${stats.profit.format()}"
    }
}

