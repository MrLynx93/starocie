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

    /** A price typed then re-read must survive the round trip unchanged. */
    @Test
    fun input_text_round_trips_through_parsing() {
        for (minor in listOf(0L, 5L, 99L, 100L, 4762L, 999_99L)) {
            val money = Money(minor)
            assertEquals(money, parseMoney(money.toInputText()), "round trip for $minor")
        }
    }
}
