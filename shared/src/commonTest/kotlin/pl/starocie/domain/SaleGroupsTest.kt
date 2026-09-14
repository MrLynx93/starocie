package pl.starocie.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SaleGroupsTest {

    /** The case this exists for: ten rings, rung up one at a time. */
    @Test
    fun the_same_thing_sold_again_and_again_is_one_line() {
        val rings = (1..10).map { item("r$it", name = "Pierścionek", status = ItemStatus.SOLD) }
        val sells = rings.map { sell("s${it.id}", it.id, 1500) }
        val ledger = Ledger(items = rings, sells = sells)

        val groups = ledger.saleGroups(sells)

        val group = groups.single()
        assertEquals(10, group.pieces)
        assertEquals(Money(15000), group.proceeds)
        assertEquals(Money(15000), group.profit)
        assertNull(group.cost, "never recorded as bought, so the cost stays unknown")
        assertEquals("r1", group.item?.id, "the first sale handed in is the one opened")
    }

    @Test
    fun a_lot_sold_a_piece_at_a_time_is_one_line_and_keeps_its_exact_shares() {
        val ledger = Ledger(
            buys = listOf(buy("b", price = 1000)),
            items = listOf(item("cups", buyId = "b", quantity = 3, status = ItemStatus.SOLD)),
            sells = listOf(
                sell("s1", "cups", 500, soldCompletely = false),
                sell("s2", "cups", 500, soldCompletely = false),
                sell("s3", "cups", 500),
            ),
        )

        val group = ledger.saleGroups(ledger.sells).single()

        assertEquals(3, group.pieces)
        assertEquals(Money(1000), group.cost?.cost, "shares of 3,34 + 3,33 + 3,33 still sum to the lot")
        assertEquals(Money(500), group.profit)
    }

    @Test
    fun prices_are_compared_per_piece() {
        val ledger = Ledger(
            items = listOf(
                item("a", name = "Talerz", quantity = 2, status = ItemStatus.SOLD),
                item("b", name = "talerz ", status = ItemStatus.SOLD),
            ),
            sells = listOf(sell("s1", "a", 3000, quantity = 2), sell("s2", "b", 1500)),
        )

        assertEquals(3, ledger.saleGroups(ledger.sells).single().pieces)
    }

    @Test
    fun a_different_price_or_cost_keeps_its_own_line() {
        val ledger = Ledger(
            buys = listOf(buy("b1", price = 500), buy("b2", price = 700)),
            items = listOf(
                item("a", name = "Lampa", buyId = "b1", status = ItemStatus.SOLD),
                item("b", name = "Lampa", buyId = "b2", status = ItemStatus.SOLD),
                item("c", name = "Lampa", buyId = "b1x", status = ItemStatus.SOLD),
            ),
            sells = listOf(
                sell("s1", "a", 1500),
                sell("s2", "b", 1500),
                sell("s3", "c", 2000),
            ),
        )

        assertEquals(3, ledger.saleGroups(ledger.sells).size)
    }

    @Test
    fun a_photographed_thing_only_joins_its_own_sales() {
        val photographed = item("a", name = "Wazon", quantity = 2).copy(photo = "jpeg")
        val ledger = Ledger(
            items = listOf(photographed, item("b", name = "Wazon", status = ItemStatus.SOLD)),
            sells = listOf(
                sell("s1", "a", 1000, soldCompletely = false),
                sell("s2", "b", 1000),
                sell("s3", "a", 1000, soldCompletely = false),
            ),
        )

        val groups = ledger.saleGroups(ledger.sells)

        assertEquals(listOf(listOf("s1", "s3"), listOf("s2")), groups.map { g -> g.sells.map { it.id } })
    }

    @Test
    fun sales_of_deleted_things_never_collapse() {
        val ledger = Ledger(sells = listOf(sell("s1", "gone", 1000), sell("s2", "gone", 1000)))

        assertEquals(2, ledger.saleGroups(ledger.sells).size)
    }
}
