package pl.starocie.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.TimeZone

/**
 * Whether a giełda on screen is the one a sale would land in. The item screen hangs
 * its "Sprzedaj" button on this, so a wrong answer either hides the button at the
 * stall we are standing at or writes today's sale into a day that has been and gone.
 */
class CurrentEventTest {

    private val resolver = CurrentEventResolver(zone = TimeZone.UTC)

    @Test
    fun today_is_current() {
        val now = resolver.eventIdFor(T0)
        assertTrue(resolver.isCurrent(now, T0))
    }

    @Test
    fun another_day_is_not() {
        val yesterday = resolver.eventIdFor(T0 - 24.hours)
        assertFalse(resolver.isCurrent(yesterday, T0))
    }

    /**
     * An extra event somebody made by hand today has a UUID, and every write still
     * resolves to the dated id — so the sale would not reach it. Matching on the day
     * rather than the id is exactly the mistake that would put the button there.
     */
    @Test
    fun a_hand_made_event_on_today_is_not_current() {
        assertFalse(resolver.isCurrent("2f0b1c9a-1111-2222-3333-444455556666", T0))
    }
}
