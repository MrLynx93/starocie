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

    /** A price typed then re-read must survive the round trip unchanged. */
    @Test
    fun input_text_round_trips_through_parsing() {
        for (minor in listOf(0L, 5L, 99L, 100L, 4762L, 999_99L)) {
            val money = Money(minor)
            assertEquals(money, parseMoney(money.toInputText()), "round trip for $minor")
        }
    }
}
