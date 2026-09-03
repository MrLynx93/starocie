package pl.starocie.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import pl.starocie.domain.ItemStats
import pl.starocie.domain.Money

/**
 * What a row says we paid.
 *
 * A lot is read by the piece, because the price beside it on the row is what one of
 * them is asked at — a lot's whole cost against a single piece's ask is a gap that
 * is not there, and on a list of things to sell that is the one lie that costs money.
 * A guess still says so on either shape, and a thing with no buy behind it still says
 * we do not know rather than quietly reading as free.
 */
class BoughtForLabelTest {

    private fun stats(cost: Money?, estimated: Boolean = false) = ItemStats(
        sellCount = 0,
        soldQuantity = 0,
        proceeds = Money.ZERO,
        cost = cost,
        costIsEstimated = estimated,
        profit = Money.ZERO,
        profitIsEstimated = estimated,
        soldAt = null,
    )

    @Test
    fun a_single_thing_says_what_it_cost() {
        assertEquals("Kupiliśmy za 40,00 zł", boughtForLabel(stats(Money(4000))))
    }

    @Test
    fun a_lot_says_what_one_of_them_cost() {
        assertEquals(
            "Kupiliśmy po 15,00 zł za sztukę",
            boughtForLabel(stats(Money(4500)), pieces = 3),
        )
    }

    @Test
    fun a_guess_says_so_either_way() {
        assertEquals(
            "Kupiliśmy za ok. 40,00 zł",
            boughtForLabel(stats(Money(4000), estimated = true)),
        )
        assertEquals(
            "Kupiliśmy po ok. 15,00 zł za sztukę",
            boughtForLabel(stats(Money(4500), estimated = true), pieces = 3),
        )
    }

    /** Never a zero: a cost we never paid is not a cost of nothing. */
    @Test
    fun an_unknown_cost_stays_unknown() {
        assertEquals("Nie wiemy, za ile kupiliśmy", boughtForLabel(stats(null)))
        assertEquals("Nie wiemy, za ile kupiliśmy", boughtForLabel(stats(null), pieces = 3))
    }
}
