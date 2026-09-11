package pl.starocie.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import pl.starocie.domain.item

/**
 * The magazyn list, narrowed the two ways it can be narrowed.
 *
 * The unpriced filter is the one question the search box cannot answer: a thing with
 * no asking price has nothing to type. What is worth pinning down is that the two
 * narrowings survive each other — a filter left on while a name is typed has to keep
 * answering about the unpriced ones, and turning it off must give the priced ones
 * back rather than leaving the list wherever it was.
 */
class NarrowedForStockTest {

    private val stock = listOf(
        item("lampa", price = 4000),
        item("waza"),
        item("wazon", price = 2500),
        item("talerz"),
    )

    @Test
    fun nothing_asked_for_leaves_the_list_alone() {
        assertEquals(4, stock.narrowedForStock(query = "", onlyUnpriced = false).size)
    }

    @Test
    fun the_filter_keeps_only_the_things_we_have_not_priced() {
        val shown = stock.narrowedForStock(query = "", onlyUnpriced = true)

        assertEquals(setOf("waza", "talerz"), shown.map { it.name }.toSet())
        assertTrue(shown.all { it.price == null })
    }

    @Test
    fun the_filter_and_the_typing_narrow_together() {
        // "waz" finds both the priced wazon and the unpriced waza; with the filter on
        // only the one we still have to price is left.
        assertEquals(
            setOf("waza", "wazon"),
            stock.narrowedForStock(query = "waz", onlyUnpriced = false).map { it.name }.toSet(),
        )
        assertEquals(
            listOf("waza"),
            stock.narrowedForStock(query = "waz", onlyUnpriced = true).map { it.name },
        )
    }

    @Test
    fun turning_the_filter_off_gives_the_priced_ones_back() {
        assertEquals(
            stock.map { it.id }.toSet(),
            stock.narrowedForStock(query = "", onlyUnpriced = false).map { it.id }.toSet(),
        )
    }

    /** A price of zero is a price somebody typed; only a missing one is unpriced. */
    @Test
    fun a_thing_priced_at_nothing_is_still_priced() {
        val shown = listOf(item("gratis", price = 0)).narrowedForStock("", onlyUnpriced = true)

        assertTrue(shown.isEmpty(), "0,00 zł is a decision, not a gap")
    }
}
