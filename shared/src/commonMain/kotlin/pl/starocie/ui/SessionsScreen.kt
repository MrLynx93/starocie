package pl.starocie.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import pl.starocie.domain.Event
import pl.starocie.domain.EventStats
import pl.starocie.domain.LedgerRepository
import pl.starocie.domain.Money
import pl.starocie.domain.format

/**
 * Our days, newest first — one list or the other of them.
 *
 * The magazyn and the sold list answer questions about things; this one answers them
 * about days. An [Event] is the app's only notion of a day — it is what everything is
 * grouped by — so the list is simply the events, and each row says what the day cost,
 * what it brought in and what it made.
 *
 * [buying] picks which days: the giełdy, being the days we sold something on, or the
 * days we only shopped. They are complementary by construction, so nothing is in both
 * lists and nothing that happened is in neither — a day of only buying is where an
 * afternoon's spending went, and it was previously nowhere but the magazyn.
 *
 * One screen for the two because they are the same question about the same kind of
 * thing, and a second copy of it would be two lists of days that could disagree about
 * how a day is drawn.
 *
 * A row opens the day, the way a row opens a thing in the other two lists.
 */
@Composable
fun SessionsScreen(
    buying: Boolean = false,
    onOpenSession: (String) -> Unit,
    onDone: () -> Unit,
) {
    val repository: LedgerRepository = koinInject()
    val ledger by repository.ledger.collectAsState()

    // The same search the other two lists carry, behind the same magnifier and with the
    // same words: typing is how anything is found in this app, and a list of days gets
    // long the same way a list of things does. See `Search.kt`.
    val search = rememberSearchState()
    var query by remember { mutableStateOf("") }

    // Under the keyboard the bottom margin is rows nobody can see.
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val typing by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }

    // Newest first, the way both other lists run: the day just had is the one being
    // asked about. Two events on one day fall back to when they were made.
    //
    // Which days these are is the ledger's rule, not this screen's, and it is the
    // same rule the home card above each list counts by — so a list and its card can
    // never disagree about how many days there have been. A day we sold nothing on is
    // not a giełda: an event is made by buying as readily as by selling, so a trip to
    // somebody's garage would otherwise sit in that list claiming to have been a
    // market. It is in the other list instead, which is what that one is for.
    // "Dawno temu" is in neither, being a filing cabinet rather than a day we had.
    val sessions = remember(ledger, query, buying) {
        (if (buying) ledger.buyingSessions() else ledger.sellingSessions())
            .filter { query.isBlank() || it.matchesQuery(query) }
            .sortedWith(compareByDescending<Event> { it.date }.thenByDescending { it.createdAt })
            .map { it to ledger.eventStats(it) }
    }

    ScreenColumn(bottom = if (typing) 6.dp else 20.dp) {
        // No figures under this heading: a giełda is a day, and days do not add up to
        // a day. There is nothing above the box to be computed over what it found.
        SearchableHeader(
            heading = if (buying) "Nasze zakupy" else "Nasze giełdy",
            search = search,
            query = query,
            onQueryChange = { query = it },
        )

        Spacer(Modifier.height(if (search.isOpen) 8.dp else 12.dp))

        if (sessions.isEmpty()) {
            Text(
                when {
                    query.isNotBlank() && buying -> "Takiego dnia zakupów nie mieliśmy."
                    query.isNotBlank() -> "Na takiej giełdzie nie byliśmy."
                    buying -> "Nie mamy jeszcze dnia samych zakupów."
                    else -> "Nie byliśmy jeszcze na żadnej giełdzie."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(sessions, key = { (event, _) -> event.id }) { (event, stats) ->
                    SessionRow(event, stats) { onOpenSession(event.id) }
                }
            }
        }

        // Nothing under the rows while the search is open: this list has no button of
        // its own, so what the typing found runs all the way to the keyboard.
        if (!search.isOpen) {
            Spacer(Modifier.height(12.dp))
            BackButton(onDone)
        }
    }
}

/**
 * What a day is found by: both of the things a row shows.
 *
 * The date is in here and not only the name, because most giełdy are auto-created and
 * never named — for those the date is the whole of what the row says, so leaving it
 * out would make the search unable to find the majority of the list. It matches the
 * text as shown, so "2026-08" finds a month and "Dawno" finds the bucket.
 */
internal fun Event.matchesQuery(query: String): Boolean =
    name?.contains(query, ignoreCase = true) == true ||
        date.asText().contains(query, ignoreCase = true)

/**
 * One day. The name is what we called it if we called it anything, and the date is
 * always there underneath — an auto-created giełda has no name and its date is the
 * whole of what it is.
 */
@Composable
private fun SessionRow(event: Event, stats: EventStats, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(event.name ?: event.date.asText(), fontWeight = FontWeight.Medium)
            if (event.name != null) {
                Text(
                    event.date.asText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SessionFigures(stats)
        }

        Spacer(Modifier.width(12.dp))

        SessionOutcome(stats)
    }
}

/**
 * What happened that day, each half with its count and its money — read one under the
 * other, because a giełda is a day of doing both.
 *
 * Selling leads, buying follows: a giełda is a day of selling that we also buy on, and
 * the takings are the thing being looked for. A day that did only one of the two says
 * only that one: a count of nought beside a sum of nought is a line about nothing.
 *
 * They are never subtracted from one another here or anywhere else: the things we
 * bought are almost never the things we sold, so the gap between these two numbers is
 * not what we made. That answer is [SessionOutcome]'s, and it comes from somewhere else
 * entirely.
 *
 * A day we only shopped on has no such answer, and its spending takes the figure's
 * place instead ([spentShownApart]) — so the buying line here drops the sum and keeps
 * the count, one number on a row being enough for it to be read once.
 *
 * Each is one line and stays one line. A count and a sum read as a single fact, and
 * wrapped in half they read as two — worse here than anywhere, because the line
 * underneath is the other half of the pair and a four-line block has no obvious order
 * left. The width is not ours to spend either: [SessionOutcome] takes what it needs
 * first and this column lives on the remainder. That is why the things are [rzeczy]
 * here and przedmioty everywhere else — the short word is what makes the line fit at
 * all, rather than merely trimming one that already did.
 */
@Composable
internal fun SessionFigures(stats: EventStats) {
    // Each line only when that half of the day happened. "Sprzedaliśmy 0 rzeczy za
    // 0,00 zł" is not a fact about a day of shopping, it is a sentence with nothing
    // in it — and on the days-of-buying list every row would carry one.
    if (stats.sellCount > 0) {
        Text(
            "Sprzedaliśmy ${rzeczy(stats.itemsSold)} za ${stats.earned.format()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (stats.buyCount > 0) {
        Text(
            if (stats.spentShownApart) {
                "Kupiliśmy ${rzeczy(stats.itemsBought)}"
            } else {
                "Kupiliśmy ${rzeczy(stats.itemsBought)} za ${stats.spent.format()}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The day's one big figure: what it made, or — on a day we only shopped on — what it
 * cost.
 *
 * What it made is each sale against what that thing cost us, never the day's takings
 * against the day's spending. A day with no sale has no such answer and must not claim
 * a nought: nothing was sold, so there is no profit and no loss, only stock. What it
 * has instead is the money that left our hands, which is the whole of what such a day
 * was — and it belongs here, in the place the eye already goes for a day's figure,
 * rather than at the end of the line saying how many things we carried home.
 *
 * A day with neither sale nor buy says nothing at all, there being no figure it is
 * short of.
 */
@Composable
internal fun SessionOutcome(stats: EventStats, style: TextStyle? = null) {
    if (stats.spentShownApart) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                stats.spent.format(),
                style = style ?: MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Wydaliśmy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    if (stats.sellCount == 0) return

    val lost = stats.profit.minor < 0
    val approx = if (stats.profitIsEstimated) "ok. " else ""
    val color = if (lost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = if (lost) {
                "$approx${Money(-stats.profit.minor).format()}"
            } else {
                "$approx${stats.profit.format()}"
            },
            style = style ?: MaterialTheme.typography.titleMedium,
            color = color,
        )
        Text(
            text = if (lost) "Straciliśmy" else "Zarobiliśmy",
            style = MaterialTheme.typography.bodySmall,
            color = if (lost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Whether the day's spending is the figure on the right rather than the tail of the
 * line on the left.
 *
 * It is one rule in one place because two composables have to agree about it: the
 * figure appears once, and the line that would otherwise carry the same sum drops it.
 * A day with a sale has a profit to put there and keeps the sum where it was.
 */
internal val EventStats.spentShownApart: Boolean
    get() = sellCount == 0 && buyCount > 0
