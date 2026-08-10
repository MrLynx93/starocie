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
     * A shortcut taken at the stall cost us nothing on the books, so the whole of what
     * it went for is what we made — while the cost itself stays the unknown it is.
     */
    @Test
    fun an_item_with_no_buy_has_unknown_cost_and_the_price_as_its_profit() {
        val ledger = Ledger(
            items = listOf(item("vase", buyId = null, status = ItemStatus.SOLD)),
            sells = listOf(sell("s1", "vase", 3000)),
        )

        val stats = ledger.itemStats(ledger.itemById("vase")!!)

        assertEquals(Money(3000), stats.proceeds)
        assertNull(stats.cost, "cost is unknown, not zero")
        assertEquals(Money(3000), stats.profit, "nothing went out, so it is all profit")
    }

    /** The identity that largest-remainder rounding exists to preserve. */
    @Test
    fun a_box_profit_equals_the_sum_of_its_item_profits() {
        val ledger = boxLedger()
        val buyStats = ledger.buyStats(buy("box", price = 10_000))

        val itemProfitSum = ledger.items
            .filter { it.buyId == "box" }
            .map { ledger.itemStats(it).profit }
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

    /**
     * The one figure on a giełda that *is* profit, and it is not the day's takings
     * less the day's spending: those two are different things' money.
     */
    @Test
    fun event_profit_is_each_sale_against_what_that_thing_cost() {
        val ledger = boxLedger()
        val sellingDay = ledger.eventStats(event("2026-08-02", D1))

        // 55.00 - 35.71, 20.00 - 21.43 and 10.00 - 17.86, which is nothing like the
        // 85.00 that earned-minus-spent would have claimed.
        assertEquals(Money(1000), sellingDay.profit)
        assertTrue(sellingDay.profitIsEstimated, "shares of a box are guesses")
    }

    /** A shortcut sale had no cost, so the day keeps the whole of what it took. */
    @Test
    fun a_sale_with_no_buy_behind_it_counts_its_whole_price_as_the_days_profit() {
        val ledger = Ledger(
            events = listOf(event("2026-08-02", D1)),
            buys = listOf(buy("b1", price = 1000)),
            items = listOf(
                item("lamp", buyId = "b1", status = ItemStatus.SOLD),
                item("mug", status = ItemStatus.SOLD),
            ),
            sells = listOf(sell("s1", "lamp", 2500), sell("s2", "mug", 4000)),
        )

        val stats = ledger.eventStats(event("2026-08-02", D1))

        assertEquals(Money(6500), stats.earned)
        assertEquals(
            Money(5500),
            stats.profit,
            "25.00 less the 10.00 it cost, plus the whole 40.00 nothing was paid for",
        )
    }

    /**
     * A deleted thing takes its cost with it, which leaves its sale in the same place
     * as a sale of something we never recorded buying: nothing to set the price
     * against. It counts for the whole of it, so the day still agrees with its rows.
     */
    @Test
    fun a_sale_whose_item_is_gone_counts_its_whole_price_too() {
        val ledger = Ledger(
            events = listOf(event("2026-08-02", D1)),
            sells = listOf(sell("s1", "gone", 4000)),
        )

        assertNull(
            ledger.sellCost(ledger.sellsOfEvent("2026-08-02").single()),
            "there is no item left to cost it against",
        )
        assertEquals(Money(4000), ledger.eventStats(event("2026-08-02", D1)).profit)
    }

    /**
     * A few pieces out of a lot carry their share of it, not the whole cost — and the
     * pieces still unsold hold theirs back rather than loading it onto what went.
     */
    @Test
    fun a_sale_of_part_of_a_lot_costs_its_share_of_the_lot() {
        val ledger = Ledger(
            events = listOf(event("2026-08-02", D1)),
            buys = listOf(buy("b1", price = 6000)),
            items = listOf(item("plates", buyId = "b1", quantity = 12)),
            sells = listOf(sell("s1", "plates", 2000, quantity = 3, soldCompletely = false)),
        )

        val cost = ledger.sellCost(ledger.sellsOfEvent("2026-08-02").single())!!

        assertEquals(Money(1500), cost.cost, "three of twelve at 60.00")
        assertTrue(cost.isEstimated, "a share is a division, however exact the price")
        assertEquals(Money(500), ledger.eventStats(event("2026-08-02", D1)).profit)
    }

    /**
     * The same reason a box's shares must sum to its price: otherwise the giełda and
     * the item disagree about the very same sales, by a grosz nobody can find.
     */
    @Test
    fun a_lots_sales_share_its_cost_to_the_grosz() {
        val ledger = Ledger(
            events = listOf(event("2026-08-02", D1)),
            buys = listOf(buy("b1", price = 9286)),
            items = listOf(item("plates", buyId = "b1", quantity = 12, status = ItemStatus.SOLD)),
            sells = listOf(
                sell("s1", "plates", 1300, quantity = 9, soldCompletely = false),
                sell("s2", "plates", 1300, quantity = 3),
            ),
        )

        val shares = ledger.sellsOfEvent("2026-08-02").map { ledger.sellCost(it)!!.cost }
        assertEquals(Money(9286), shares.sum(), "naive division drops a grosz here")

        assertEquals(
            ledger.itemStats(ledger.itemById("plates")!!).profit,
            ledger.eventStats(event("2026-08-02", D1)).profit,
            "the day and the thing must agree about the same sales",
        )
    }

    /** Both counts are pieces, so they answer for the same things the money does. */
    @Test
    fun a_day_counts_pieces_rather_than_records() {
        val ledger = Ledger(
            events = listOf(event("2026-08-01", D0), event("2026-08-02", D1)),
            buys = listOf(buy("b1", price = 6000)),
            items = listOf(item("plates", buyId = "b1", quantity = 12)),
            sells = listOf(sell("s1", "plates", 2000, quantity = 3, soldCompletely = false)),
        )

        assertEquals(12, ledger.eventStats(event("2026-08-01", D0)).itemsBought)
        assertEquals(3, ledger.eventStats(event("2026-08-02", D1)).itemsSold)
    }

    /**
     * An event is made by buying as readily as by selling, so a trip to somebody's
     * garage produces one exactly like a market does. It is not a giełda, and
     * counting it would say we have been somewhere we never went.
     */
    @Test
    fun a_day_we_only_bought_on_is_not_a_giełda() {
        val ledger = Ledger(
            events = listOf(event("2026-08-01", D0), event("2026-08-02", D1)),
            buys = listOf(buy("b1", price = 1000, eventId = "2026-08-01")),
            items = listOf(item("lamp", buyId = "b1", status = ItemStatus.SOLD)),
            sells = listOf(sell("s1", "lamp", 2500, eventId = "2026-08-02")),
        )

        assertEquals(
            listOf("2026-08-02"),
            ledger.sellingSessions().map { it.id },
            "the day we bought on took nothing and made nothing",
        )
    }

    /** What was bought on such a day still counts toward what we have spent. */
    @Test
    fun a_buy_only_day_still_counts_toward_what_we_spent() {
        val ledger = Ledger(
            events = listOf(event("2026-08-01", D0), event("2026-08-02", D1)),
            buys = listOf(
                buy("b1", price = 1000, eventId = "2026-08-01"),
                buy("b2", price = 400, eventId = "2026-08-02"),
            ),
            items = listOf(item("lamp", buyId = "b1", status = ItemStatus.SOLD)),
            sells = listOf(sell("s1", "lamp", 2500, eventId = "2026-08-02")),
        )

        val overall = ledger.overallStats()

        assertEquals(Money(1400), overall.spent, "both days' money, giełda or not")
        assertEquals(Money(1500), overall.profit)
        assertEquals(1, overall.sellCount)
    }

    /**
     * "Dawno temu" files the purchase of a thing that was never recorded until it
     * sold. It only ever holds buys, so the one rule keeps it out too.
     */
    @Test
    fun the_long_ago_bucket_is_not_a_giełda_either() {
        val ledger = Ledger(
            events = listOf(event(LongAgo.EVENT_ID, LongAgo.DATE), event("2026-08-02", D1)),
            buys = listOf(buy("b1", price = 1000, eventId = LongAgo.EVENT_ID)),
            items = listOf(item("vase", buyId = "b1", status = ItemStatus.SOLD)),
            sells = listOf(sell("s1", "vase", 2500, eventId = "2026-08-02")),
        )

        assertEquals(listOf("2026-08-02"), ledger.sellingSessions().map { it.id })
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
