package pl.starocie.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pl.starocie.domain.format
import pl.starocie.domain.sum

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
 * [selling] is the single difference: coming in from "Sprzedaj" adds the button for
 * a thing that was never recorded at all. From the magazyn it is absent, because
 * you did not come here to buy anything.
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

    // Over what is on screen rather than over the whole magazyn: with a search
    // running, the total that matters is the total of what was found.
    val shownValue = remember(state.inStock) {
        state.inStock.mapNotNull { it.item.price }.sum()
    }

    ScreenColumn {
        Text("Nasz magazyn", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Mamy ${przedmioty(state.inStock.size)} · " +
                "Chcemy sprzedać za łącznie ${shownValue.format()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            singleLine = true,
            placeholder = { Text("Czego szukasz?") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        if (state.inStock.isEmpty()) {
            Text(
                if (state.query.isBlank()) "Nic tu jeszcze nie mamy." else "Nic takiego nie mamy.",
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

            Spacer(Modifier.height(10.dp))
        }

        BackButton(onDone)
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
