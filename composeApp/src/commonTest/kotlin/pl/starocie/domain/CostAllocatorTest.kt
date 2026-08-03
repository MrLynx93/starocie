package pl.starocie.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CostAllocatorTest {

    /**
     * The worked example from the design: 100.00 across asking prices of
     * 50.00 / 30.00 / 25.00 / none.
     *
     * The unpriced item takes the mean of the other three (35.00), so the weights
     * total 140.00 and the exact shares are 35.7143 / 21.4286 / 17.8571 / 25.0000.
     * Flooring reaches only 99.98, so the two spare grosz go to the largest
     * remainders — the 30.00 item (.857) and the 25.00 item (.714).
     */
    @Test
    fun allocates_the_worked_example() {
        val items = listOf(
            item("cup", price = 5000),
            item("candle", price = 3000),
            item("book", price = 2500),
            item("plate", price = null),
        )

        val shares = CostAllocator.allocate(Money(10_000), items)

        assertEquals(Money(3571), shares["cup"])
        assertEquals(Money(2143), shares["candle"])
        assertEquals(Money(1786), shares["book"])
        assertEquals(Money(2500), shares["plate"])
    }

    /**
     * The property that matters most: a buy's profit only equals the sum of its
     * items' profits if no grosz is lost in the split.
     */
    @Test
    fun shares_always_sum_to_the_total() {
        val cases = listOf(
            Money(10_000) to listOf(item("a", 5000), item("b", 3000), item("c", 2500)),
            Money(10_000) to listOf(item("a"), item("b"), item("c")),
            Money(1) to listOf(item("a", 100), item("b", 100), item("c", 100)),
            Money(7) to listOf(item("a", 1), item("b", 2), item("c", 5), item("d", 11)),
            Money(999_983) to listOf(item("a", 7), item("b", 11), item("c", 13)),
            Money(0) to listOf(item("a", 500), item("b", 300)),
            Money(12_345) to List(17) { item("i$it", price = (it + 1) * 37L) },
        )

        for ((total, items) in cases) {
            val shares = CostAllocator.allocate(total, items)
            assertEquals(
                total.minor,
                shares.values.sumOf { it.minor },
                "shares must sum to $total for ${items.size} items",
            )
            assertTrue(shares.values.all { it.minor >= 0 }, "no share may be negative")
        }
    }

    @Test
    fun splits_evenly_when_nothing_is_priced() {
        val shares = CostAllocator.allocate(
            Money(10_000),
            listOf(item("a"), item("b"), item("c")),
        )

        // 3333 each leaves one grosz over; every remainder ties, so the lowest id wins.
        assertEquals(Money(3334), shares["a"])
        assertEquals(Money(3333), shares["b"])
        assertEquals(Money(3333), shares["c"])
    }

    @Test
    fun unpriced_items_take_the_mean_of_the_priced_ones() {
        val shares = CostAllocator.allocate(
            Money(400),
            listOf(item("a", 100), item("b", 300), item("c", null)),
        )

        // Mean of 100 and 300 is 200, so weights are 100 / 300 / 200 of 600.
        assertEquals(Money(67), shares["a"])
        assertEquals(Money(200), shares["b"])
        assertEquals(Money(133), shares["c"])
        assertEquals(400, shares.values.sumOf { it.minor })
    }

    @Test
    fun a_sole_item_takes_the_whole_total() {
        val shares = CostAllocator.allocate(Money(4000), listOf(item("only", price = 9999)))

        assertEquals(Money(4000), shares["only"])
    }

    /**
     * Two phones may hold the same items in different order. If allocation depended
     * on list position they would compute different costs for the same data.
     */
    @Test
    fun is_independent_of_list_order() {
        val items = listOf(
            item("cup", 5000),
            item("candle", 3000),
            item("book", 2500),
            item("plate", null),
        )

        val expected = CostAllocator.allocate(Money(10_000), items)

        for (permutation in listOf(items.reversed(), items.shuffled(), items.sortedBy { it.id })) {
            assertEquals(expected, CostAllocator.allocate(Money(10_000), permutation))
        }
    }

    @Test
    fun falls_back_to_an_even_split_when_every_price_is_zero() {
        val shares = CostAllocator.allocate(
            Money(300),
            listOf(item("a", 0), item("b", 0), item("c", 0)),
        )

        assertEquals(Money(100), shares["a"])
        assertEquals(Money(100), shares["b"])
        assertEquals(Money(100), shares["c"])
    }

    @Test
    fun handles_an_empty_buy() {
        assertEquals(emptyMap(), CostAllocator.allocate(Money(5000), emptyList()))
    }

    @Test
    fun rejects_a_negative_total() {
        assertFailsWith<IllegalArgumentException> {
            CostAllocator.allocate(Money(-1), listOf(item("a"), item("b")))
        }
    }
}
