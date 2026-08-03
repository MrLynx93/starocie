package pl.starocie.domain

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The single place the auto-grouping rule lives.
 *
 * An auto-created event's id **is** the ISO date. That is what makes it safe
 * offline: at a market both phones are likely to be offline, and with random ids
 * each device would invent its own "today" and produce two events for one day.
 * A deterministic id means both write the same document and converge on reconnect.
 */
class CurrentEventResolver(
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    fun dateOf(instant: Instant): LocalDate = instant.toLocalDateTime(zone).date

    /** Auto-created events are keyed by their date, e.g. `2026-08-01`. */
    fun eventIdFor(date: LocalDate): String = date.toString()

    fun eventIdFor(instant: Instant): String = eventIdFor(dateOf(instant))
}
