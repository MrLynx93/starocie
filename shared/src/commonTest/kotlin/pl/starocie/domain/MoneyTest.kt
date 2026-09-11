package pl.starocie.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {

    @Test
    fun formats_polish_style() {
        assertEquals("47,62 zł", Money(4762).format())
        assertEquals("0,05 zł", Money(5).format())
        assertEquals("-15,00 zł", Money(-1500).format())
    }

    @Test
    fun input_text_drops_empty_decimals() {
        assertEquals("50", Money(5000).toInputText())
        assertEquals("47,62", Money(4762).toInputText())
    }

    @Test
    fun parses_both_separators_and_rejects_nonsense() {
        assertEquals(Money(1250), parseMoney("12,50"))
        assertEquals(Money(1250), parseMoney("12.5"))
        assertEquals(Money(1200), parseMoney(" 12 "))
        assertNull(parseMoney("abc"))
        assertNull(parseMoney(""))
        assertNull(parseMoney("-5"))
    }

    /**
     * A lot's cost read back per piece. Nothing stores the result — it is what a
     * per-piece field opens on — so a total that will not divide evenly loses the
     * odd grosz here rather than in the books.
     */
    @Test
    fun divides_a_total_back_into_pieces() {
        assertEquals(Money(3000), Money(9000) / 3)
        assertEquals(Money(3333), Money(10000) / 3)
        assertEquals(Money(9000), Money(9000) / 1)
        assertEquals(Money(9000), Money(9000) / 0, "nothing is divided by no pieces")
    }

    /**
     * The same money per piece over a different count — what a corrected lot is
     * worth. Rounded rather than divided out and multiplied back, so a count moved
     * and moved back lands on the price it started at.
     */
    @Test
    fun scales_a_total_to_a_new_count_at_the_same_rate() {
        assertEquals(Money(12000), Money(9000).atSameRate(was = 3, now = 4))
        assertEquals(Money(9000), Money(9000).atSameRate(was = 3, now = 3))
        assertEquals(Money(9000), Money(1500).atSameRate(was = 1, now = 6))

        // 100,00 over three does not divide evenly, and the odd grosz must survive
        // the trip out and back rather than being lost on the way.
        val there = Money(10000).atSameRate(was = 3, now = 4)
        assertEquals(Money(13333), there)
        assertEquals(Money(10000), there.atSameRate(was = 4, now = 3))
    }

    /**
     * The buy forms open their price at zero, so almost every price is typed on top
     * of one. Losing that zero is what keeps "015" from being what a 15 zł purchase
     * looks like, and keeping it is what leaves "0,50" a price.
     */
    @Test
    fun typing_over_a_zero_drops_it() {
        assertEquals("15", typedPrice("015"))
        assertEquals("1", typedPrice("01"))
        assertEquals("0,50", typedPrice("0,50"))
        assertEquals("0.5", typedPrice("0.5"))
        assertEquals("0", typedPrice("0"))
        assertEquals("0", typedPrice("00"))
        assertEquals("", typedPrice(""))
        assertEquals("12", typedPrice("12"))
    }

    /** A price typed then re-read must survive the round trip unchanged. */
    @Test
    fun input_text_round_trips_through_parsing() {
        for (minor in listOf(0L, 5L, 99L, 100L, 4762L, 999_99L)) {
            val money = Money(minor)
            assertEquals(money, parseMoney(money.toInputText()), "round trip for $minor")
        }
    }
}
