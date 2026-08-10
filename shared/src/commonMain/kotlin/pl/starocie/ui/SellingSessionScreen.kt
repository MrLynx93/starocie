package pl.starocie.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import pl.starocie.domain.Event
import pl.starocie.domain.EventStats
import pl.starocie.domain.LedgerRepository
import pl.starocie.domain.Money
import pl.starocie.domain.format

/**
 * Every giełda we have been to, newest first.
 *
 * The magazyn and the sold list answer questions about things; this one answers them
 * about days. An [Event] is the app's only notion of a market day — it is what
 * everything is grouped by — so the list is simply the events, and each row says what
 * the day cost, what it brought in and what it made.
 *
 * A row opens the day, the way a row opens a thing in the other two lists.
 */
@Composable
fun SellingSessionScreen(onOpenSession: (String) -> Unit, onDone: () -> Unit) {
    val repository: LedgerRepository = koinInject()
    val ledger by repository.ledger.collectAsState()

    // The same search box the other two lists carry, in the same place and with the
    // same words: typing is how anything is found in this app, and a list of days
    // gets long the same way a list of things does.
    var query by remember { mutableStateOf("") }

    // Newest first, the way both other lists run: today's giełda is the one being
    // asked about. Two events on one day fall back to when they were made.
    //
    // The days we sold nothing on are not here, because they are not giełdy: an
    // event is made by buying as readily as by selling, so a trip to somebody's
    // garage would otherwise sit in this list claiming to have been a market. It is
    // the same rule the home card counts by, so the two cannot disagree — and it
    // covers "Dawno temu" as well, that being a filing cabinet holding only buys.
    // What was bought on such a day is still in the magazyn, where it is found.
    val sessions = remember(ledger, query) {
        ledger.sellingSessions()
            .filter { query.isBlank() || it.matchesQuery(query) }
            .sortedWith(compareByDescending<Event> { it.date }.thenByDescending { it.createdAt })
            .map { it to ledger.eventStats(it) }
    }

    ScreenColumn {
        Text("Nasze giełdy", style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Czego szukamy?") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        if (sessions.isEmpty()) {
            Text(
                if (query.isBlank()) {
                    "Nie byliśmy jeszcze na żadnej giełdzie."
                } else {
                    "Na takiej giełdzie nie byliśmy."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(sessions, key = { (event, _) -> event.id }) { (event, stats) ->
                    SessionRow(event, stats) { onOpenSession(event.id) }
                    HorizontalDivider()
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        BackButton(onDone)
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
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

        SessionProfit(stats)
    }
}

/**
 * The two things that happened that day, each with its count and its money — read one
 * under the other, because a giełda is a day of doing both.
 *
 * Selling leads, buying follows: a giełda is a day of selling that we also buy on, and
 * the takings are the thing being looked for.
 *
 * They are never subtracted from one another here or anywhere else: the things we
 * bought are almost never the things we sold, so the gap between these two numbers is
 * not what we made. That answer is [SessionProfit]'s, and it comes from somewhere else
 * entirely.
 */
@Composable
internal fun SessionFigures(stats: EventStats) {
    Text(
        "Sprzedaliśmy ${przedmioty(stats.itemsSold)} za ${stats.earned.format()}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "Kupiliśmy ${przedmioty(stats.itemsBought)} za ${stats.spent.format()}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    unknownCostNote(stats)?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/**
 * What the day made — each sale against what that thing cost us, not the day's takings
 * against the day's spending.
 *
 * A day we only bought on has nothing to say here, and says nothing rather than
 * claiming a nought: no sale means no profit and no loss, only stock.
 */
@Composable
internal fun SessionProfit(stats: EventStats, style: TextStyle? = null) {
    if (stats.sellCount == 0) return

    val allUnknown = stats.sellsOfUnknownCost == stats.sellCount
    val lost = stats.profit.minor < 0
    val approx = if (stats.profitIsEstimated) "ok. " else ""
    val color = if (lost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = when {
                allUnknown -> "Nie wiemy"
                lost -> "$approx${Money(-stats.profit.minor).format()}"
                else -> "$approx${stats.profit.format()}"
            },
            style = style ?: MaterialTheme.typography.titleMedium,
            color = if (allUnknown) MaterialTheme.colorScheme.onSurfaceVariant else color,
        )
        Text(
            text = when {
                allUnknown -> "Ile zarobiliśmy"
                lost -> "Straciliśmy"
                else -> "Zarobiliśmy"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (lost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * What the profit above had to leave out.
 *
 * A sale of something we never recorded buying has no cost to be measured against, and
 * counting its whole price as gain would inflate the day every time a shortcut was
 * taken. It is left out instead, and this is the line that admits it.
 */
internal fun unknownCostNote(stats: EventStats): String? = when {
    stats.sellsOfUnknownCost == 0 -> null
    stats.sellsOfUnknownCost == stats.sellCount -> "Nie wiemy, za ile to kupiliśmy"
    else -> "Przy ${sprzedażach(stats.sellsOfUnknownCost)} nie wiemy, za ile kupiliśmy"
}

/**
 * The count rule once more, in the locative — it is only ever read after "przy":
 * "przy 1 sprzedaży", "przy 3 sprzedażach".
 */
internal fun sprzedażach(count: Int): String =
    "$count " + if (count == 1) "sprzedaży" else "sprzedażach"
