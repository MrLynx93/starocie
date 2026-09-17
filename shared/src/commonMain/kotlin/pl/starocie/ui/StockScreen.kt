package pl.starocie.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pl.starocie.domain.format

/**
 * Everything we are holding: one list, searched, whichever way you came in.
 *
 * There used to be two of these — a search box for selling and a browse list for
 * looking — and they were the same list twice. Typing a name is how you find a
 * thing whether you are about to sell it or just checking we still have it, so the
 * search belongs to both; and a row that behaved differently depending on which
 * door you used is exactly the kind of thing that gets learned wrong once and then
 * costs money.
 *
 * A row opens the item, always. Selling, correcting a price and deleting all live
 * there, one screen away from the list, where there is room to read before acting.
 *
 * [selling] is the difference, and it changes three things. Coming in from "Sprzedaj"
 * adds the button for a thing that was never recorded at all — from the magazyn it is
 * absent, because you did not come here to buy anything — the heading becomes the
 * question being answered, "Co chcesz sprzedać?" rather than "Nasz magazyn", and the
 * "Niewycenione przedmioty" filter goes, being a question for the magazyn between
 * giełdy rather than for somebody holding a thing out. The list's rows stay exactly
 * the same either way.
 *
 * The heading matters most coming from a giełda, which is the one door where the
 * screen behind is a place rather than a list: without it, a day's "Sprzedaj" landed
 * on a page that gave no sign the sale was still going to be recorded into that day.
 */
@Composable
fun StockScreen(
    selling: Boolean,
    onOpenItem: (String) -> Unit,
    onAddNew: () -> Unit,
    onDone: () -> Unit,
) {
    val viewModel: SellViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The keyboard is up only while the search box is being typed into, and then the
    // screen is for the rows: the heading's figures, "Wstecz" and most of the bottom
    // margin go until it is put away, so what the typing found is what fills the
    // space left. Leaving is still one system back away — the first closes the
    // keyboard and brings the button back.
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val typing by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }

    ScreenColumn(bottom = if (typing) 8.dp else 20.dp) {
        // Opened to sell from, the heading is the question being answered — the same
        // second person the search box under it uses, and for the same reason: it is
        // the app asking the person holding the phone, not the notebook saying what we
        // did. It is also what says this is a step in selling rather than the magazyn
        // arrived at, which a giełda's own "Sprzedaj" had no other way to show.
        Text(
            if (selling) "Co chcesz sprzedać?" else "Nasz magazyn",
            style = MaterialTheme.typography.headlineSmall,
        )

        if (!typing) {
            // Over the magazyn, not over what the search found: the heading says what
            // we have, and looking for one thing does not change that. The unpriced
            // filter does move it, being a question about the magazyn rather than a
            // search through it.
            //
            // The asking total is what the list is worth, and with the unpriced ones
            // on their own there is no such number — every one of them is the gap.
            // "Chcemy sprzedać za łącznie 0,00 zł" would be the app answering a
            // question it has just been told nobody can answer yet.
            val summary = if (state.onlyUnpriced) {
                "Jeszcze ${if (state.shelfCount == 1) "go" else "ich"} nie wyceniliśmy"
            } else {
                "Chcemy sprzedać za łącznie ${state.shelfValue.format()}"
            }

            Text(
                "Mamy ${przedmioty(state.shelfCount)} · $summary",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            singleLine = true,
            placeholder = { Text("Czego szukasz?") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        // The one thing the search box cannot find: a thing with no asking price has
        // nothing to type. It sits under the box because it narrows the same list in
        // the same way, and it is only drawn while there is something to find — a
        // switch that can only ever empty the list is a line about nothing.
        //
        // Only from the magazyn card, though. It is what the list is read for between
        // giełdy — what still needs a price before the next one — and at the stall the
        // thing being looked for is in somebody's hand, priced or not. Each route owns
        // its own view model, so the selling list can never inherit the filter on.
        if (!selling && state.offersUnpricedFilter) {
            Spacer(Modifier.height(10.dp))

            // A tick while it is on, and the slot empty while it is off: the chip's
            // colour alone says "selected" to somebody who already knows chips, and
            // this list is read in daylight by two people who are counting plates.
            val tick: @Composable () -> Unit = {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }

            FilterChip(
                selected = state.onlyUnpriced,
                onClick = { viewModel.onOnlyUnpricedChange(!state.onlyUnpriced) },
                label = { Text("Niewycenione przedmioty") },
                leadingIcon = if (state.onlyUnpriced) tick else null,
                shape = RoundedCornerShape(14.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        if (state.inStock.isEmpty()) {
            Text(
                when {
                    state.query.isNotBlank() -> "Nic takiego nie mamy."
                    // Reachable only by pricing the last one with the filter on, and
                    // then it is the answer rather than an empty list.
                    state.onlyUnpriced -> "Wszystko mamy już wycenione."
                    else -> "Nic tu jeszcze nie mamy."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.inStock, key = { it.item.id }) { entry ->
                    StockRow(
                        entry.item,
                        entry.stats,
                        entry.piecesLeft,
                        onClick = { onOpenItem(entry.item.id) },
                    )
                    HorizontalDivider()
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // The thing may never have been recorded — most of the time, at the start,
        // it has not been. It sits at the bottom with the other buttons rather than
        // under the search box, where it used to push the list down a line every
        // time the typing stopped matching anything.
        if (selling) {
            Button(
                onClick = { viewModel.startNewItem(); onAddNew() },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    if (state.query.isBlank()) {
                        "Dodaj nowy przedmiot i sprzedaj"
                    } else {
                        "Dodaj \"${state.query.trim()}\" i sprzedaj"
                    },
                    fontWeight = FontWeight.Medium,
                )
            }

            if (!typing) Spacer(Modifier.height(10.dp))
        }

        if (!typing) BackButton(onDone)
    }
}

/**
 * "1 przedmiotów" is the kind of small wrongness that makes an app feel automated,
 * so the word follows the full Polish rule: one takes the bare word, a tail of 2–4
 * takes "przedmioty", and everything else "przedmiotów" — with the teens carved
 * out, because 12 counts like 5 and not like 2.
 */
internal fun przedmioty(count: Int): String {
    val tail = count % 10
    val teens = count % 100 in 12..14
    val word = when {
        count == 1 -> "przedmiot"
        tail in 2..4 && !teens -> "przedmioty"
        else -> "przedmiotów"
    }
    return "$count $word"
}

/**
 * The short word for the same thing, and the giełda screens' alone: "Sprzedaliśmy 1
 * rzecz za …", "…2 rzeczy za…", "…12 rzeczy za…".
 *
 * A row there says what the day sold and what it cost on two lines that also carry
 * money, beside a profit that has first claim on the width — "przedmiotów" is what
 * pushes those onto a second line, and a wrapped figure is harder to read than a
 * blunter word. Everywhere a thing has room to be named properly it is still a
 * [przedmioty]; this is the one place the width decides.
 *
 * Plural is "rzeczy" for both the 2–4 tail and the rest, so only one takes a form of
 * its own — the teens need no carving out here.
 */
internal fun rzeczy(count: Int): String = "$count ${if (count == 1) "rzecz" else "rzeczy"}"

/**
 * The same rule again for market days, also in the accusative: "Mamy za sobą 1
 * giełdę", "…2 giełdy", "…12 giełd".
 */
internal fun giełdy(count: Int): String {
    val tail = count % 10
    val teens = count % 100 in 12..14
    val word = when {
        count == 1 -> "giełdę"
        tail in 2..4 && !teens -> "giełdy"
        else -> "giełd"
    }
    return "$count $word"
}

/**
 * Days we only went shopping on, counted: "1 dzień zakupów", "2 dni zakupów", "12
 * dni zakupów".
 *
 * Only the one takes a form of its own here, "dni" covering both the 2–4 tail and
 * the rest, so there is no teens exception to carve out. It is a whole phrase rather
 * than a word because "3 zakupy" would be three purchases, which is not what is being
 * counted: a day of them is.
 */
internal fun dniZakupów(count: Int): String =
    "$count ${if (count == 1) "dzień" else "dni"} zakupów"

/**
 * The same rule for pieces of a lot, in the accusative — this one is always read as
 * the object of something we are doing: "Sprzedajemy 1 sztukę za", "…3 sztuki za",
 * "…12 sztuk za".
 */
internal fun sztuki(count: Int): String {
    val tail = count % 10
    val teens = count % 100 in 12..14
    val word = when {
        count == 1 -> "sztukę"
        tail in 2..4 && !teens -> "sztuki"
        else -> "sztuk"
    }
    return "$count $word"
}
