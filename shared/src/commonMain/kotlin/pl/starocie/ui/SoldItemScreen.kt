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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.datetime.LocalDate
import pl.starocie.domain.ItemStats
import pl.starocie.domain.LedgerRepository
import pl.starocie.domain.Money
import pl.starocie.domain.Sell
import pl.starocie.domain.format
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
 * The profit sits at the top and is recomputed from those fields as they change,
 * which is what tells you the correction landed. It is the only thing on the screen
 * that is not editable, because it is not a fact anybody entered.
 *
 * A lot that went in several sales gets a pair of fields per sale, since each one
 * happened on its own day for its own money. There is no "Usuń" here: deleting
 * belongs to the magazyn, where a thing still exists to be got rid of, and erasing
 * a sold item would only lose the proceeds it is the record of.
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
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(item != null) {
        if (item != null) seen = true else if (seen) onDone()
    }

    if (item == null) return

    val stats = remember(ledger, item) { ledger.itemStats(item) }
    val buy = item.buyId?.let { ledger.buyById(it) }
    val sells = remember(ledger, item) { ledger.sellsOfItem(item.id) }

    // What was paid is the buy's, not the item's — so with several things in one
    // buy the field edits the price of the box, and has to say that it does.
    val isPartOfABox = item.buyId != null && ledger.itemCountOfBuy(item.buyId) > 1

    var paidText by remember(item.id) { mutableStateOf(buy?.price?.toInputText() ?: "") }

    ScreenColumn {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(item.name, style = MaterialTheme.typography.headlineSmall)
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

            MoneyField(
                label = if (isPartOfABox) "Całą paczkę kupiliśmy za" else "Kupiliśmy za",
                text = paidText,
                onTextChange = { paidText = it },
                saved = buy?.price,
                placeholder = "Nie wiemy",
                // An exact cost and a guess must never look alike: with several
                // things in one buy this is the box's price, and the item's own cost
                // is only a share of it.
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

            Spacer(Modifier.height(24.dp))

            // The heading carries the total, which is why there is no separate line
            // adding the sales up underneath them.
            if (item.splittable) {
                SectionLabel(
                    if (sells.size > 1) {
                        "Sprzedaliśmy za ${stats.proceeds.format()} w ${sells.size} kawałkach"
                    } else {
                        "Sprzedaliśmy za ${stats.proceeds.format()}"
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
                    onPriceSave = { viewModel.setSellPrice(sell.id, it) },
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

        BackButton(onDone)
    }
}

/**
 * The day and the price of one sale, which is the whole of what a sale is once it
 * has happened.
 *
 * The text is held here, keyed by the sale, so a lot's several sales cannot share a
 * field between them — and so an edit survives the ledger echoing the write back.
 */
@Composable
private fun SaleFields(
    sell: Sell,
    caption: String?,
    onDateChange: (LocalDate) -> Unit,
    onPriceSave: (String) -> Unit,
) {
    var priceText by remember(sell.id) { mutableStateOf(sell.price.toInputText()) }

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

        MoneyField(
            label = "Sprzedaliśmy za",
            text = priceText,
            onTextChange = { priceText = it },
            saved = sell.price,
            placeholder = "Za ile poszło",
            onSave = onPriceSave,
        )
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

/** True only when we know the profit *and* it went the wrong way. */
internal val ItemStats.isALoss: Boolean get() = profit != null && profit.minor < 0

/**
 * What the sale came to, said rather than signed: a loss is something we lost, not
 * a gain with a minus in front of it, and a guess says so.
 */
internal fun profitLabel(stats: ItemStats): String {
    val profit = stats.profit ?: return "Nie wiemy, ile na tym zarobiliśmy"
    val approx = if (stats.profitIsEstimated) "ok. " else ""
    return if (stats.isALoss) {
        "Straciliśmy $approx${Money(-profit.minor).format()}"
    } else {
        "Zarobiliśmy $approx${profit.format()}"
    }
}

