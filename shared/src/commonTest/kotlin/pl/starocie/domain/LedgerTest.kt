package pl.starocie.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LedgerTest {

    @Test
    fun a_sole_item_gets_an_exact_cost() {
        val ledger = Ledger(
            buys = listOf(buy("b1", price = 4000)),
            items = listOf(item("lamp", price = 7000, buyId = "b1", status = ItemStatus.SOLD)),
            sells = listOf(sell("s1", "lamp", 6500)),
        )

        val stats = ledger.itemStats(ledger.itemById("lamp")!!)

        assertEquals(Money(4000), stats.cost)
        assertFalse(stats.costIsEstimated, "a one-item buy is measured, not estimated")
        assertEquals(Money(2500), stats.profit)
        assertFalse(stats.profitIsEstimated)
    }

    @Test
    fun an_item_from_a_box_gets_an_estimated_cost() {
        val ledger = boxLedger()
        val stats = ledger.itemStats(ledger.itemById("cup")!!)

        assertEquals(Money(3571), stats.cost)
        assertTrue(stats.costIsEstimated, "a share of a box is a guess and must say so")
        assertEquals(Money(1929), stats.profit)
        assertTrue(stats.profitIsEstimated)
    }

    /**
     * A shortcut taken at the stall must never look like pure profit, or margins
     * inflate every time the app is used the way it was designed to be used.
     */
    @Test
    fun an_item_with_no_buy_has_unknown_cost_and_unknown_profit() {
        val ledger = Ledger(
            items = listOf(item("vase", buyId = null, status = ItemStatus.SOLD)),
            sells = listOf(sell("s1", "vase", 3000)),
        )

        val stats = ledger.itemStats(ledger.itemById("vase")!!)

        assertEquals(Money(3000), stats.proceeds)
        assertNull(stats.cost, "cost is unknown, not zero")
        assertNull(stats.profit, "profit is unknown, not equal to the proceeds")
    }

    /** The identity that largest-remainder rounding exists to preserve. */
    @Test
    fun a_box_profit_equals_the_sum_of_its_item_profits() {
        val ledger = boxLedger()
        val buyStats = ledger.buyStats(buy("box", price = 10_000))

        val itemProfitSum = ledger.items
            .filter { it.buyId == "box" }
            .mapNotNull { ledger.itemStats(it).profit }
            .sum()

        assertEquals(Money(-1500), buyStats.profit)
        assertEquals(buyStats.profit, itemProfitSum)
    }

    @Test
    fun a_removed_item_resolves_its_buy_and_books_its_share_as_a_loss() {
        val ledger = boxLedger()

        val plate = ledger.itemStats(ledger.itemById("plate")!!)
        assertEquals(Money(2500), plate.cost)
        assertEquals(Money.ZERO, plate.proceeds)
        assertEquals(Money(-2500), plate.profit, "a broken plate costs what it cost")

        val buyStats = ledger.buyStats(buy("box", price = 10_000))
        assertEquals(4, buyStats.itemCount)
        assertEquals(4, buyStats.resolvedItemCount)
        assertTrue(buyStats.fullyResolved, "removed counts as resolved, or the box never closes")
    }

    @Test
    fun a_splittable_lot_accumulates_sells_until_one_closes_it() {
        val ledger = Ledger(
            buys = listOf(buy("b1", price = 6000)),
            items = listOf(
                item("plates", price = 7500, buyId = "b1", quantity = 12, status = ItemStatus.SOLD),
            ),
            sells = listOf(
                sell("s1", "plates", 3000, soldCompletely = false),
                sell("s2", "plates", 2500, soldCompletely = false),
                sell("s3", "plates", 1500, soldCompletely = true),
            ),
        )

        val stats = ledger.itemStats(ledger.itemById("plates")!!)

        assertEquals(3, stats.sellCount)
        assertEquals(Money(7000), stats.proceeds)
        assertEquals(Money(6000), stats.cost)
        assertEquals(Money(1000), stats.profit)
        assertEquals(T0, stats.soldAt, "soldAt comes from the closing sell")
    }

    @Test
    fun an_unsold_box_reports_partial_recovery_rather_than_a_final_loss() {
        val ledger = boxLedger(candleStatus = ItemStatus.IN_STOCK, sellCandle = false)
        val buyStats = ledger.buyStats(buy("box", price = 10_000))

        assertFalse(buyStats.fullyResolved)
        assertEquals(Money(10_000), buyStats.cost)
        assertEquals(Money(6500), buyStats.proceeds)
    }

    /** Cash flow, not profit: what is sold on a day is rarely what was bought there. */
    @Test
    fun event_stats_are_cash_in_and_cash_out() {
        val ledger = boxLedger()

        val buyingDay = ledger.eventStats(event("2026-08-01", D0))
        assertEquals(Money(10_000), buyingDay.spent)
        assertEquals(Money.ZERO, buyingDay.earned)
        assertEquals(4, buyingDay.itemsBought)

        val sellingDay = ledger.eventStats(event("2026-08-02", D1))
        assertEquals(Money.ZERO, sellingDay.spent)
        assertEquals(Money(8500), sellingDay.earned)
        assertEquals(3, sellingDay.itemsSold)
    }

    @Test
    fun in_stock_excludes_sold_and_removed() {
        val inStock = boxLedger(candleStatus = ItemStatus.IN_STOCK, sellCandle = false)
            .itemsInStock()
            .map { it.id }

        assertEquals(listOf("candle"), inStock)
    }

    /**
     * A 100.00 box of four: cup, candle and book priced 50 / 30 / 25, plus an
     * unpriced plate that later breaks. Allocation is 35.71 / 21.43 / 17.86 / 25.00.
     */
    private fun boxLedger(
        candleStatus: ItemStatus = ItemStatus.SOLD,
        sellCandle: Boolean = true,
    ) = Ledger(
        events = listOf(event("2026-08-01", D0), event("2026-08-02", D1)),
        buys = listOf(buy("box", price = 10_000)),
        items = listOf(
            item("cup", price = 5000, buyId = "box", status = ItemStatus.SOLD),
            item("candle", price = 3000, buyId = "box", status = candleStatus),
            item("book", price = 2500, buyId = "box", status = ItemStatus.SOLD),
            item("plate", price = null, buyId = "box", status = ItemStatus.REMOVED),
        ),
        sells = buildList {
            add(sell("s1", "cup", 5500))
            if (sellCandle) add(sell("s2", "candle", 2000))
            add(sell("s3", "book", 1000))
        },
    )
}
